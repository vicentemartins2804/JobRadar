package com.jobrador.backend.model;

public enum EmailCategory {
	/** Job alert, recruiter outreach or newsletter listing open positions. */
	JOB_OPENING,
	/** "We received your application" confirmations. */
	APPLICATION_CONFIRMATION,
	/** Progress on an application: in review, assessment/test, next steps. */
	APPLICATION_UPDATE,
	/** Interview invitation, scheduling or confirmation. */
	INTERVIEW,
	OFFER,
	REJECTION,
	NOT_JOB_RELATED
}
