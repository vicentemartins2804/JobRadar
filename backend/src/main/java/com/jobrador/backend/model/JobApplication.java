package com.jobrador.backend.model;

import java.time.LocalDate;

public record JobApplication(
		Long id,
		String company,
		String role,
		ApplicationStatus status,
		LocalDate appliedDate,
		LocalDate lastUpdate,
		String nextStep) {
}
