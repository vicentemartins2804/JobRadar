package com.jobrador.backend.model;

import java.time.OffsetDateTime;
import java.util.List;

/** Everything the frontend shows, already organized into sections. */
public record JobDashboard(
		List<Opening> openings,
		List<Application> applications,
		List<Interview> interviews,
		OffsetDateTime lastSyncAt,
		int emailsAnalyzed,
		SyncStatus sync) {

	/** One job from a job alert / recruiter email. */
	public record Opening(
			String emailId,
			String company,
			String role,
			String location,
			String url,
			OffsetDateTime receivedAt,
			MailFolder folder,
			String emailSubject) {
	}

	/** All emails about the same company + role, collapsed into one entry with the latest status. */
	public record Application(
			String company,
			String role,
			EmailCategory status,
			OffsetDateTime firstSeenAt,
			OffsetDateTime lastUpdateAt,
			String nextStep,
			String summary,
			int emailCount,
			boolean foundInJunk,
			List<TimelineEvent> timeline) {
	}

	/**
	 * One email in an application's history, e.g. "applied" → "interview" → "offer".
	 * company/role are this email's own values (what a manual correction edits).
	 */
	public record TimelineEvent(
			String emailId,
			OffsetDateTime at,
			EmailCategory status,
			String company,
			String role,
			String summary,
			String emailSubject,
			MailFolder folder,
			boolean corrected) {
	}

	public record Interview(
			String emailId,
			String company,
			String role,
			String interviewAt,
			String nextStep,
			String summary,
			OffsetDateTime receivedAt,
			MailFolder folder) {
	}

}
