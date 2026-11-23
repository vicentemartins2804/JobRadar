package com.jobrador.backend.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record JobOpening(
		@JsonPropertyDescription("Hiring company. Empty string if unknown.")
		String company,
		@JsonPropertyDescription("Job title.")
		String role,
		@JsonPropertyDescription("City/country or 'Remote'. Empty string if not mentioned.")
		String location,
		@JsonPropertyDescription("Link to the job posting exactly as it appears in the email. Empty string if none.")
		String url) {
}
