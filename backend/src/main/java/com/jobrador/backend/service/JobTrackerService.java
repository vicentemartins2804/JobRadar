package com.jobrador.backend.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.jobrador.backend.client.ClaudeEmailAnalyzer;
import com.jobrador.backend.client.MicrosoftGraphClient;
import com.jobrador.backend.model.AnalyzedEmail;
import com.jobrador.backend.model.Email;
import com.jobrador.backend.model.EmailAnalysis;
import com.jobrador.backend.model.EmailCategory;
import com.jobrador.backend.model.EmailCorrection;
import com.jobrador.backend.model.JobDashboard;
import com.jobrador.backend.model.MailFolder;
import com.jobrador.backend.model.SyncStatus;
import com.jobrador.backend.repository.AnalyzedEmailRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JobTrackerService {

	/** How far back the very first sync looks. */
	static final Period FIRST_SYNC_PERIOD = Period.ofMonths(3);

	/** Later syncs re-read a few minutes before the last one, in case of late-delivered emails. */
	static final Duration SYNC_OVERLAP = Duration.ofMinutes(10);

	private static final Set<EmailCategory> APPLICATION_CATEGORIES = Set.of(
			EmailCategory.APPLICATION_CONFIRMATION, EmailCategory.APPLICATION_UPDATE,
			EmailCategory.INTERVIEW, EmailCategory.OFFER, EmailCategory.REJECTION);

	private final MicrosoftGraphClient graph;
	private final ClaudeEmailAnalyzer analyzer;
	private final AnalyzedEmailRepository repository;
	private final Executor analysisExecutor;

	/** Users with a sync in progress, so pressing "Sync" twice doesn't start two. */
	private final Set<String> runningSyncs = ConcurrentHashMap.newKeySet();

	public JobTrackerService(MicrosoftGraphClient graph, ClaudeEmailAnalyzer analyzer,
			AnalyzedEmailRepository repository, @Qualifier("emailAnalysisExecutor") Executor analysisExecutor) {
		this.graph = graph;
		this.analyzer = analyzer;
		this.repository = repository;
		this.analysisExecutor = analysisExecutor;
	}

	/**
	 * Fetches new emails from Inbox + Junk (first sync: last 3 months; afterwards: since the last sync),
	 * then analyzes them in the background. Returns right after fetching; poll {@link #getDashboard}
	 * to follow progress.
	 */
	public JobDashboard sync(String userId) {
		if (!runningSyncs.add(userId)) {
			return getDashboard(userId);
		}
		try {
			fetchNewEmails(userId);
		} catch (RuntimeException ex) {
			runningSyncs.remove(userId);
			throw ex;
		}
		analyzePendingInBackground(userId);
		return getDashboard(userId);
	}

	/** Applies a manual correction to one analyzed email. Empty if the user has no such email. */
	public Optional<JobDashboard> correct(String userId, String emailId, EmailCorrection correction) {
		return repository.findAnalyzed(userId, emailId).map(email -> {
			repository.saveAnalyzed(userId, email.correctedWith(correction));
			return getDashboard(userId);
		});
	}

	public JobDashboard getDashboard(String userId) {
		return buildDashboard(repository.findAll(userId), repository.lastSyncAt(userId).orElse(null),
				repository.syncStatus(userId));
	}

	/** Must run on the request thread: Graph calls use the logged-in user's token from the session. */
	private void fetchNewEmails(String userId) {
		OffsetDateTime fetchStartedAt = OffsetDateTime.now();
		OffsetDateTime since = repository.fetchedUntil(userId)
				.map(last -> last.minus(SYNC_OVERLAP))
				.orElse(fetchStartedAt.minus(FIRST_SYNC_PERIOD));

		List<Email> fetched = new ArrayList<>();
		for (MailFolder folder : MailFolder.values()) {
			fetched.addAll(graph.getEmailsSince(folder, since));
		}

		Set<String> alreadyAnalyzed = repository.findAnalyzedIds(userId);
		List<Email> fresh = fetched.stream().filter(e -> !alreadyAnalyzed.contains(e.id())).toList();
		repository.addPending(userId, fresh);
		repository.setFetchedUntil(userId, fetchStartedAt);
		log.info("Sync since {}: {} emails fetched, {} new", since, fetched.size(), fresh.size());
	}

	private void analyzePendingInBackground(String userId) {
		List<Email> pending = repository.findPending(userId);
		int total = pending.size();
		AtomicInteger analyzed = new AtomicInteger();
		AtomicInteger failed = new AtomicInteger();
		repository.setSyncStatus(userId, new SyncStatus(true, total, 0, 0));

		CompletableFuture<?>[] tasks = pending.stream()
				.map(email -> CompletableFuture.runAsync(() -> {
					try {
						repository.saveAnalyzed(userId, AnalyzedEmail.of(email, analyzeOrSkip(email)));
						analyzed.incrementAndGet();
					} catch (Exception ex) {
						// Stays pending, so it's retried on the next sync
						failed.incrementAndGet();
						log.warn("Could not analyze email {}: {}", email.id(), ex.getMessage());
					}
					repository.setSyncStatus(userId, new SyncStatus(true, total, analyzed.get(), failed.get()));
				}, analysisExecutor))
				.toArray(CompletableFuture[]::new);

		CompletableFuture.allOf(tasks).whenComplete((ignored, ex) -> {
			repository.setSyncStatus(userId, new SyncStatus(false, total, analyzed.get(), failed.get()));
			repository.setLastSyncAt(userId, OffsetDateTime.now());
			runningSyncs.remove(userId);
			log.info("Sync finished: {} analyzed, {} failed", analyzed.get(), failed.get());
		});
	}

	/** If Claude declines an email, store it as not job related so it isn't retried forever. */
	private EmailAnalysis analyzeOrSkip(Email email) {
		return analyzer.analyze(email).orElseGet(() ->
				new EmailAnalysis(EmailCategory.NOT_JOB_RELATED, "", "", "", "", "", List.of()));
	}

	static JobDashboard buildDashboard(List<AnalyzedEmail> emails, OffsetDateTime lastSyncAt, SyncStatus sync) {
		Comparator<AnalyzedEmail> newestFirst = Comparator.comparing(AnalyzedEmail::receivedAt,
				Comparator.nullsLast(Comparator.reverseOrder()));
		List<AnalyzedEmail> sorted = emails.stream().sorted(newestFirst).toList();

		return new JobDashboard(openings(sorted), applications(sorted), interviews(sorted), lastSyncAt,
				emails.size(), sync);
	}

	private static List<JobDashboard.Opening> openings(List<AnalyzedEmail> newestFirst) {
		// Same job often arrives in several alerts: keep only the most recent one
		Map<String, JobDashboard.Opening> unique = new LinkedHashMap<>();
		newestFirst.stream()
				.filter(e -> e.analysis().category() == EmailCategory.JOB_OPENING && e.analysis().openings() != null)
				.forEach(e -> e.analysis().openings().forEach(o -> unique.putIfAbsent(
						key(o.company(), o.role()),
						new JobDashboard.Opening(e.emailId(), o.company(), o.role(), o.location(), o.url(),
								e.receivedAt(), e.folder(), e.subject()))));
		return List.copyOf(unique.values());
	}

	private static List<JobDashboard.Application> applications(List<AnalyzedEmail> newestFirst) {
		Map<String, List<AnalyzedEmail>> byJob = newestFirst.stream()
				.filter(e -> APPLICATION_CATEGORIES.contains(e.analysis().category()))
				.collect(Collectors.groupingBy(e -> key(companyOf(e), e.analysis().role()),
						LinkedHashMap::new, Collectors.toList()));

		return byJob.values().stream().map(group -> {
			AnalyzedEmail latest = group.getFirst();
			AnalyzedEmail first = group.getLast();
			return new JobDashboard.Application(
					companyOf(latest),
					firstNonBlank(group.stream().map(e -> e.analysis().role()).toList()),
					latest.analysis().category(),
					first.receivedAt(),
					latest.receivedAt(),
					latest.analysis().nextStep(),
					latest.analysis().summary(),
					group.size(),
					group.stream().anyMatch(e -> e.folder() == MailFolder.JUNK),
					timeline(group));
		}).toList();
	}

	/** The group's emails as steps, oldest first (group is newest first). */
	private static List<JobDashboard.TimelineEvent> timeline(List<AnalyzedEmail> newestFirst) {
		return newestFirst.reversed().stream()
				.map(e -> new JobDashboard.TimelineEvent(e.emailId(), e.receivedAt(), e.analysis().category(),
						companyOf(e), e.analysis().role(), e.analysis().summary(), e.subject(), e.folder(),
						e.corrected()))
				.toList();
	}

	private static List<JobDashboard.Interview> interviews(List<AnalyzedEmail> newestFirst) {
		return newestFirst.stream()
				.filter(e -> e.analysis().category() == EmailCategory.INTERVIEW)
				.map(e -> new JobDashboard.Interview(e.emailId(), companyOf(e), e.analysis().role(),
						e.analysis().interviewAt(),
						e.analysis().nextStep(), e.analysis().summary(), e.receivedAt(), e.folder()))
				.toList();
	}

	/** Falls back to the sender's name when Claude couldn't tell the company. */
	private static String companyOf(AnalyzedEmail e) {
		String company = e.analysis().company();
		return company == null || company.isBlank() ? e.fromName() : company;
	}

	private static String firstNonBlank(List<String> values) {
		return values.stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
	}

	private static String key(String company, String role) {
		return normalize(company) + "|" + normalize(role);
	}

	private static String normalize(String s) {
		return s == null ? "" : s.strip().toLowerCase(Locale.ROOT);
	}

}
