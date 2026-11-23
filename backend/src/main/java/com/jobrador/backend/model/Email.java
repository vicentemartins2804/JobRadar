package com.jobrador.backend.model;

import java.time.OffsetDateTime;

public record Email(
		String id,
		MailFolder folder,
		String subject,
		String fromName,
		String fromAddress,
		OffsetDateTime receivedAt,
		String body) {
}
