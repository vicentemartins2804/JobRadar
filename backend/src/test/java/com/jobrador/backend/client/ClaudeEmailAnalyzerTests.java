package com.jobrador.backend.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.jobrador.backend.model.Email;
import com.jobrador.backend.model.MailFolder;

class ClaudeEmailAnalyzerTests {

	@Test
	void rendersEmailWithMetadata() {
		Email email = new Email("A", MailFolder.JUNK, "Interview", "Farfetch", "jobs@farfetch.com",
				OffsetDateTime.parse("2026-11-20T09:30:00Z"), "  Hello  ");

		String rendered = ClaudeEmailAnalyzer.render(email);

		assertThat(rendered)
			.contains("<folder>JUNK</folder>")
			.contains("<subject>Interview</subject>")
			.contains("jobs@farfetch.com")
			.contains("\nHello\n");
	}

	@Test
	void buildsRequestWithValidOutputSchema() {
		ClaudeEmailAnalyzer analyzer = new ClaudeEmailAnalyzer(
				AnthropicOkHttpClient.builder().apiKey("test").build(), "claude-haiku-5-5");
		Email email = new Email("A", MailFolder.INBOX, "Interview", "Farfetch", "jobs@farfetch.com",
				OffsetDateTime.parse("2026-11-20T09:30:00Z"), "Hello");

		// The SDK derives and validates the JSON schema from EmailAnalysis here; throws if unsupported
		String json = analyzer.buildParams(email).rawParams()._body().toString();

		assertThat(json)
			.contains("claude-haiku-5-5")
			.contains("JOB_OPENING")
			.contains("openings");
	}

	@Test
	void capsVeryLongBodies() {
		String body = "x".repeat(ClaudeEmailAnalyzer.MAX_BODY_CHARS + 500);
		Email email = new Email("A", MailFolder.INBOX, "Alert", "LinkedIn", "jobs@linkedin.com",
				OffsetDateTime.parse("2026-11-20T09:30:00Z"), body);

		String rendered = ClaudeEmailAnalyzer.render(email);

		assertThat(rendered).contains("[... email truncated ...]");
		assertThat(rendered.length()).isLessThan(ClaudeEmailAnalyzer.MAX_BODY_CHARS + 400);
	}

}
