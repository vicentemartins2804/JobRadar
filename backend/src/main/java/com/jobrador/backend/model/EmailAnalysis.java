package com.jobrador.backend.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What Claude extracts from one email. Also used as the JSON schema for structured output,
 * so the descriptions below are instructions to the model.
 */
public record EmailAnalysis(
		@JsonPropertyDescription("Category of the email.")
		EmailCategory category,
		@JsonPropertyDescription("Company the application/interview/offer/rejection is about. Empty string for JOB_OPENING, NOT_JOB_RELATED or if unknown.")
		String company,
		@JsonPropertyDescription("Job title the application/interview/offer/rejection is about. Empty string for JOB_OPENING, NOT_JOB_RELATED or if unknown.")
		String role,
		@JsonPropertyDescription("For INTERVIEW only: interview date/time in ISO-8601 (e.g. 2026-11-26T14:00) if stated, else empty string.")
		String interviewAt,
		@JsonPropertyDescription("Concrete action the candidate should take next, in English, max 12 words. Empty string if none.")
		String nextStep,
		@JsonPropertyDescription("One-sentence summary of the email in English. Empty string for NOT_JOB_RELATED.")
		String summary,
		@JsonPropertyDescription("For JOB_OPENING only: every job listed in the email. Empty list for other categories.")
		List<JobOpening> openings) {
}
