package com.jobrador.backend.model;

/**
 * Manual fix for Claude's analysis of one email. Null fields are left unchanged.
 * Setting the same company + role as another application merges them; NOT_JOB_RELATED hides the email.
 */
public record EmailCorrection(EmailCategory category, String company, String role) {
}
