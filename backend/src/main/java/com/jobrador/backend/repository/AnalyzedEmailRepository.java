package com.jobrador.backend.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.jobrador.backend.model.AnalyzedEmail;
import com.jobrador.backend.model.Email;
import com.jobrador.backend.model.SyncStatus;

/**
 * Per-user sync state, in memory (lost on restart — to be replaced by a database):
 * <ul>
 * <li>analyzed emails, so each email is only analyzed (and paid for) once</li>
 * <li>pending emails: fetched from Outlook but not analyzed yet (or failed), retried on the next sync</li>
 * <li>fetchedUntil: the next sync only asks Outlook for emails received after this moment</li>
 * </ul>
 */
@Repository
public class AnalyzedEmailRepository {

	private final Map<String, UserData> byUser = new ConcurrentHashMap<>();

	public Set<String> findAnalyzedIds(String userId) {
		return Set.copyOf(data(userId).analyzed.keySet());
	}

	public Optional<AnalyzedEmail> findAnalyzed(String userId, String emailId) {
		return Optional.ofNullable(data(userId).analyzed.get(emailId));
	}

	public List<AnalyzedEmail> findAll(String userId) {
		return List.copyOf(data(userId).analyzed.values());
	}

	/** Stores the analysis and removes the email from the pending queue. */
	public void saveAnalyzed(String userId, AnalyzedEmail email) {
		UserData data = data(userId);
		data.analyzed.put(email.emailId(), email);
		data.pending.remove(email.emailId());
	}

	public void addPending(String userId, Collection<Email> emails) {
		Map<String, Email> pending = data(userId).pending;
		emails.forEach(e -> pending.put(e.id(), e));
	}

	public List<Email> findPending(String userId) {
		return List.copyOf(data(userId).pending.values());
	}

	public Optional<OffsetDateTime> fetchedUntil(String userId) {
		return Optional.ofNullable(data(userId).fetchedUntil);
	}

	public void setFetchedUntil(String userId, OffsetDateTime at) {
		data(userId).fetchedUntil = at;
	}

	public Optional<OffsetDateTime> lastSyncAt(String userId) {
		return Optional.ofNullable(data(userId).lastSyncAt);
	}

	public void setLastSyncAt(String userId, OffsetDateTime at) {
		data(userId).lastSyncAt = at;
	}

	public SyncStatus syncStatus(String userId) {
		return data(userId).syncStatus;
	}

	public void setSyncStatus(String userId, SyncStatus status) {
		data(userId).syncStatus = status;
	}

	private UserData data(String userId) {
		return byUser.computeIfAbsent(userId, id -> new UserData());
	}

	private static final class UserData {
		final Map<String, AnalyzedEmail> analyzed = new ConcurrentHashMap<>();
		final Map<String, Email> pending = new ConcurrentHashMap<>();
		volatile OffsetDateTime fetchedUntil;
		volatile OffsetDateTime lastSyncAt;
		volatile SyncStatus syncStatus = SyncStatus.IDLE;
	}

}
