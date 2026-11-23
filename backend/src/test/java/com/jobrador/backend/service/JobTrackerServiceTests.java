package com.jobrador.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jobrador.backend.client.ClaudeEmailAnalyzer;
import com.jobrador.backend.client.MicrosoftGraphClient;
import com.jobrador.backend.model.AnalyzedEmail;
import com.jobrador.backend.model.Email;
import com.jobrador.backend.model.EmailAnalysis;
import com.jobrador.backend.model.EmailCategory;
import com.jobrador.backend.model.EmailCorrection;
import com.jobrador.backend.model.JobDashboard;
import com.jobrador.backend.model.JobOpening;
import com.jobrador.backend.model.MailFolder;
import com.jobrador.backend.model.SyncStatus;
import com.jobrador.backend.repository.AnalyzedEmailRepository;

class JobTrackerServiceTests {

	private MicrosoftGraphClient graph;
	private ClaudeEmailAnalyzer analyzer;
	private JobTrackerService service;

	@BeforeEach
	void setUp() {
		graph = mock(MicrosoftGraphClient.class);
		analyzer = mock(ClaudeEmailAnalyzer.class);
		when(graph.getEmailsSince(any(), any())).thenReturn(List.of());
		// Runnable::run executes the "background" analysis immediately, so tests stay synchronous
		service = new JobTrackerService(graph, analyzer, new AnalyzedEmailRepository(), Runnable::run);
	}

	@Test
	void firstSyncReadsLastThreeMonthsOfInboxAndJunk() {
		service.sync("user");

		ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(graph).getEmailsSince(eq(MailFolder.INBOX), since.capture());
		verify(graph).getEmailsSince(eq(MailFolder.JUNK), any());
		assertThat(since.getValue()).isCloseTo(OffsetDateTime.now().minusMonths(3), within(1, ChronoUnit.MINUTES));
	}

	@Test
	void laterSyncsOnlyReadSinceTheLastOne() {
		service.sync("user");
		OffsetDateTime secondSyncStart = OffsetDateTime.now();
		service.sync("user");

		ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(graph, times(2)).getEmailsSince(eq(MailFolder.INBOX), since.capture());
		assertThat(since.getAllValues().get(1))
			.isCloseTo(secondSyncStart.minus(JobTrackerService.SYNC_OVERLAP), within(1, ChronoUnit.MINUTES));
	}

	@Test
	void analyzesEachEmailOnlyOnceAndReportsProgress() {
		Email inbox = email("1", MailFolder.INBOX, "2026-11-20T10:00:00Z");
		Email junk = email("2", MailFolder.JUNK, "2026-11-21T10:00:00Z");
		when(graph.getEmailsSince(eq(MailFolder.INBOX), any())).thenReturn(List.of(inbox));
		when(graph.getEmailsSince(eq(MailFolder.JUNK), any())).thenReturn(List.of(junk));
		when(analyzer.analyze(any())).thenReturn(Optional.of(analysis(EmailCategory.INTERVIEW, "Farfetch", "Dev")));

		JobDashboard first = service.sync("user");
		service.sync("user"); // overlap window returns the same emails again

		assertThat(first.emailsAnalyzed()).isEqualTo(2);
		assertThat(first.lastSyncAt()).isNotNull();
		assertThat(first.sync()).isEqualTo(new SyncStatus(false, 2, 2, 0));
		verify(analyzer, times(1)).analyze(inbox);
		verify(analyzer, times(1)).analyze(junk);
	}

	@Test
	void failedEmailStaysPendingAndIsRetriedEvenIfNotFetchedAgain() {
		Email e = email("1", MailFolder.INBOX, "2026-11-20T10:00:00Z");
		when(graph.getEmailsSince(eq(MailFolder.INBOX), any())).thenReturn(List.of(e), List.of());
		when(analyzer.analyze(e))
			.thenThrow(new RuntimeException("rate limited"))
			.thenReturn(Optional.of(analysis(EmailCategory.NOT_JOB_RELATED, "", "")));

		JobDashboard first = service.sync("user");
		JobDashboard second = service.sync("user");

		assertThat(first.sync()).isEqualTo(new SyncStatus(false, 1, 0, 1));
		assertThat(first.emailsAnalyzed()).isZero();
		assertThat(second.sync()).isEqualTo(new SyncStatus(false, 1, 1, 0));
		assertThat(second.emailsAnalyzed()).isEqualTo(1);
	}

	@Test
	void refusedEmailIsStoredAsNotJobRelated() {
		Email e = email("1", MailFolder.JUNK, "2026-11-20T10:00:00Z");
		when(graph.getEmailsSince(eq(MailFolder.JUNK), any())).thenReturn(List.of(e));
		when(analyzer.analyze(e)).thenReturn(Optional.empty());

		service.sync("user");
		service.sync("user");

		verify(analyzer, times(1)).analyze(e);
		assertThat(service.getDashboard("user").emailsAnalyzed()).isEqualTo(1);
	}

	@Test
	void correctingCompanyAndRoleMergesApplications() {
		Email applied = email("1", MailFolder.INBOX, "2026-11-01T10:00:00Z");
		Email interview = email("2", MailFolder.INBOX, "2026-11-05T10:00:00Z");
		when(graph.getEmailsSince(eq(MailFolder.INBOX), any())).thenReturn(List.of(applied, interview));
		when(analyzer.analyze(applied))
			.thenReturn(Optional.of(analysis(EmailCategory.APPLICATION_CONFIRMATION, "Farfetch", "Frontend Dev")));
		when(analyzer.analyze(interview))
			.thenReturn(Optional.of(analysis(EmailCategory.INTERVIEW, "Farfetch Portugal", "")));
		assertThat(service.sync("user").applications()).hasSize(2);

		JobDashboard fixed = service.correct("user", "2", new EmailCorrection(null, "Farfetch", "Frontend Dev"))
			.orElseThrow();

		assertThat(fixed.applications()).hasSize(1);
		JobDashboard.Application merged = fixed.applications().getFirst();
		assertThat(merged.status()).isEqualTo(EmailCategory.INTERVIEW);
		assertThat(merged.timeline()).extracting(JobDashboard.TimelineEvent::corrected).containsExactly(false, true);
		assertThat(merged.timeline().get(1).company()).isEqualTo("Farfetch");
	}

	@Test
	void markingNotJobRelatedHidesTheEmailAndSurvivesResync() {
		Email e = email("1", MailFolder.JUNK, "2026-11-01T10:00:00Z");
		when(graph.getEmailsSince(eq(MailFolder.JUNK), any())).thenReturn(List.of(e));
		when(analyzer.analyze(e)).thenReturn(Optional.of(analysis(EmailCategory.INTERVIEW, "Scam Corp", "Agent")));
		service.sync("user");

		service.correct("user", "1", new EmailCorrection(EmailCategory.NOT_JOB_RELATED, null, null));
		JobDashboard afterResync = service.sync("user");

		assertThat(afterResync.applications()).isEmpty();
		assertThat(afterResync.interviews()).isEmpty();
		verify(analyzer, times(1)).analyze(e);
	}

	@Test
	void correctingUnknownEmailReturnsEmpty() {
		assertThat(service.correct("user", "nope", new EmailCorrection(EmailCategory.OFFER, null, null))).isEmpty();
	}

	@Test
	void dashboardDoesNotCallOutlookOrClaude() {
		JobDashboard dashboard = service.getDashboard("user");

		assertThat(dashboard.sync()).isEqualTo(SyncStatus.IDLE);
		verify(graph, never()).getEmailsSince(any(), any());
		verify(analyzer, never()).analyze(any());
	}

	@Test
	void groupsApplicationEmailsByCompanyAndRoleWithLatestStatus() {
		List<AnalyzedEmail> emails = List.of(
				analyzed("1", MailFolder.INBOX, "2026-11-01T10:00:00Z",
						analysis(EmailCategory.APPLICATION_CONFIRMATION, "Farfetch", "Frontend Developer")),
				analyzed("2", MailFolder.JUNK, "2026-11-10T10:00:00Z",
						analysis(EmailCategory.INTERVIEW, "farfetch ", "frontend developer")),
				analyzed("3", MailFolder.INBOX, "2026-11-05T10:00:00Z",
						analysis(EmailCategory.REJECTION, "Feedzai", "Software Engineer")),
				analyzed("4", MailFolder.INBOX, "2026-11-06T10:00:00Z",
						analysis(EmailCategory.NOT_JOB_RELATED, "", "")));

		JobDashboard dashboard = JobTrackerService.buildDashboard(emails, null, SyncStatus.IDLE);

		assertThat(dashboard.applications()).hasSize(2);
		JobDashboard.Application farfetch = dashboard.applications().getFirst();
		assertThat(farfetch.status()).isEqualTo(EmailCategory.INTERVIEW);
		assertThat(farfetch.emailCount()).isEqualTo(2);
		assertThat(farfetch.firstSeenAt()).isEqualTo(OffsetDateTime.parse("2026-11-01T10:00:00Z"));
		assertThat(farfetch.lastUpdateAt()).isEqualTo(OffsetDateTime.parse("2026-11-10T10:00:00Z"));
		assertThat(farfetch.foundInJunk()).isTrue();
		assertThat(farfetch.timeline())
			.extracting(JobDashboard.TimelineEvent::status, JobDashboard.TimelineEvent::folder)
			.containsExactly(
				tuple(EmailCategory.APPLICATION_CONFIRMATION, MailFolder.INBOX),
				tuple(EmailCategory.INTERVIEW, MailFolder.JUNK));
		assertThat(farfetch.timeline().getFirst().at()).isEqualTo(OffsetDateTime.parse("2026-11-01T10:00:00Z"));
		assertThat(dashboard.applications().get(1).status()).isEqualTo(EmailCategory.REJECTION);

		assertThat(dashboard.interviews()).hasSize(1);
		assertThat(dashboard.interviews().getFirst().folder()).isEqualTo(MailFolder.JUNK);
	}

	@Test
	void flattensJobAlertsAndDropsDuplicateOpenings() {
		EmailAnalysis older = new EmailAnalysis(EmailCategory.JOB_OPENING, "", "", "", "", "Alert", List.of(
				new JobOpening("Talkdesk", "Java Engineer", "Lisbon", "https://a"),
				new JobOpening("OutSystems", "Full Stack Dev", "Remote", "https://b")));
		EmailAnalysis newer = new EmailAnalysis(EmailCategory.JOB_OPENING, "", "", "", "", "Alert", List.of(
				new JobOpening("Talkdesk", "java engineer", "Lisbon", "https://c")));

		JobDashboard dashboard = JobTrackerService.buildDashboard(List.of(
				analyzed("1", MailFolder.INBOX, "2026-11-01T10:00:00Z", older),
				analyzed("2", MailFolder.JUNK, "2026-11-02T10:00:00Z", newer)), null, SyncStatus.IDLE);

		assertThat(dashboard.openings()).extracting(JobDashboard.Opening::company)
			.containsExactly("Talkdesk", "OutSystems");
		assertThat(dashboard.openings().getFirst().url()).isEqualTo("https://c");
		assertThat(dashboard.applications()).isEmpty();
	}

	@Test
	void fallsBackToSenderNameWhenCompanyUnknown() {
		JobDashboard dashboard = JobTrackerService.buildDashboard(List.of(
				analyzed("1", MailFolder.INBOX, "2026-11-01T10:00:00Z",
						analysis(EmailCategory.APPLICATION_CONFIRMATION, "", "Backend Dev"))), null, SyncStatus.IDLE);

		assertThat(dashboard.applications().getFirst().company()).isEqualTo("Sender 1");
	}

	private static Email email(String id, MailFolder folder, String receivedAt) {
		return new Email(id, folder, "Subject " + id, "Sender " + id, id + "@example.com",
				OffsetDateTime.parse(receivedAt), "body");
	}

	private static AnalyzedEmail analyzed(String id, MailFolder folder, String receivedAt, EmailAnalysis analysis) {
		return AnalyzedEmail.of(email(id, folder, receivedAt), analysis);
	}

	private static EmailAnalysis analysis(EmailCategory category, String company, String role) {
		return new EmailAnalysis(category, company, role, "", "Reply to recruiter", "Summary", List.of());
	}

}
