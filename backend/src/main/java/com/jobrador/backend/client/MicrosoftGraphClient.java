package com.jobrador.backend.client;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobrador.backend.model.Email;
import com.jobrador.backend.model.MailFolder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MicrosoftGraphClient {

	static final int PAGE_SIZE = 100;

	/** Safety cap so a huge mailbox can't trigger thousands of AI calls in one sync. */
	static final int MAX_EMAILS_PER_FOLDER = 2000;

	private final RestClient graph;

	public MicrosoftGraphClient(@Qualifier("graphRestClient") RestClient graph) {
		this.graph = graph;
	}

	/**
	 * All emails in one folder of the logged-in user's mailbox received at or after {@code since},
	 * newest first, with plain-text bodies. Follows Graph's paging until the end (or the safety cap).
	 */
	public List<Email> getEmailsSince(MailFolder folder, OffsetDateTime since) {
		// ISO_INSTANT always prints seconds in UTC, e.g. 2026-05-23T10:00:00Z
		String sinceUtc = DateTimeFormatter.ISO_INSTANT.format(since.toInstant().truncatedTo(ChronoUnit.SECONDS));

		GraphMessages page = graph.get()
				.uri(uri -> uri.path("/me/mailFolders/{folder}/messages")
						.queryParam("$filter", "receivedDateTime ge " + sinceUtc)
						.queryParam("$orderby", "receivedDateTime desc")
						.queryParam("$top", PAGE_SIZE)
						.queryParam("$select", "id,subject,from,receivedDateTime,body")
						.build(folder.graphName()))
				// Ask Graph to convert HTML bodies to plain text (cheaper and cleaner for the AI)
				.header("Prefer", "outlook.body-content-type=\"text\"")
				.retrieve()
				.body(GraphMessages.class);

		List<Email> emails = new ArrayList<>();
		while (page != null) {
			if (page.value() != null) {
				page.value().forEach(m -> emails.add(toEmail(m, folder)));
			}
			if (page.nextLink() == null) {
				break;
			}
			if (emails.size() >= MAX_EMAILS_PER_FOLDER) {
				log.warn("Stopped reading {} after {} emails (safety cap)", folder, emails.size());
				break;
			}
			// nextLink is an absolute, already-encoded URL that keeps the same filter/select
			page = graph.get()
					.uri(URI.create(page.nextLink()))
					.header("Prefer", "outlook.body-content-type=\"text\"")
					.retrieve()
					.body(GraphMessages.class);
		}
		return emails;
	}

	private static Email toEmail(GraphMessages.Message m, MailFolder folder) {
		GraphMessages.EmailAddress sender = m.from() != null ? m.from().emailAddress() : null;
		return new Email(
				m.id(),
				folder,
				m.subject(),
				sender != null ? sender.name() : null,
				sender != null ? sender.address() : null,
				m.receivedDateTime(),
				m.body() != null ? m.body().content() : null);
	}

}
