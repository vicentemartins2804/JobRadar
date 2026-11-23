package com.jobrador.backend.model;

import java.time.OffsetDateTime;

/**
 * An email's metadata (without the body) plus Claude's analysis of it.
 *
 * @param corrected true once the user fixed the analysis by hand (it's never re-analyzed)
 */
public record AnalyzedEmail(
		String emailId,
		MailFolder folder,
		String subject,
		String fromName,
		String fromAddress,
		OffsetDateTime receivedAt,
		EmailAnalysis analysis,
		boolean corrected) {

	public static AnalyzedEmail of(Email email, EmailAnalysis analysis) {
		return new AnalyzedEmail(email.id(), email.folder(), email.subject(), email.fromName(),
				email.fromAddress(), email.receivedAt(), analysis, false);
	}

	/** Applies a manual correction; null fields are left unchanged. */
	public AnalyzedEmail correctedWith(EmailCorrection fix) {
		EmailAnalysis a = analysis;
		EmailAnalysis fixed = new EmailAnalysis(
				fix.category() != null ? fix.category() : a.category(),
				fix.company() != null ? fix.company().strip() : a.company(),
				fix.role() != null ? fix.role().strip() : a.role(),
				a.interviewAt(), a.nextStep(), a.summary(), a.openings());
		return new AnalyzedEmail(emailId, folder, subject, fromName, fromAddress, receivedAt, fixed, true);
	}

}
