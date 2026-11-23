package com.jobrador.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jobrador.backend.model.Email;
import com.jobrador.backend.model.MailFolder;

class MicrosoftGraphClientTests {

	private static final String NEXT_PAGE = "https://graph.microsoft.com/v1.0/me/mailFolders/junkemail/messages?$skip=100";

	private MockRestServiceServer server;
	private MicrosoftGraphClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://graph.microsoft.com/v1.0");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new MicrosoftGraphClient(builder.build());
	}

	@Test
	void readsAllPagesSinceDateWithPlainTextBodies() {
		server.expect(requestTo(startsWith("https://graph.microsoft.com/v1.0/me/mailFolders/junkemail/messages")))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("$filter", "receivedDateTime%20ge%202026-05-23T10:00:00Z"))
			.andExpect(queryParam("$top", "100"))
			.andExpect(header("Prefer", "outlook.body-content-type=\"text\""))
			.andRespond(withSuccess("""
					{
					  "@odata.context": "ignored",
					  "@odata.nextLink": "%s",
					  "value": [{
					    "id": "AAA",
					    "subject": "Interview invitation",
					    "from": { "emailAddress": { "name": "Farfetch Careers", "address": "jobs@farfetch.com" } },
					    "receivedDateTime": "2026-11-20T09:30:00Z",
					    "body": { "contentType": "text", "content": "We'd like to invite you..." }
					  }]
					}
					""".formatted(NEXT_PAGE), MediaType.APPLICATION_JSON));
		server.expect(requestTo(NEXT_PAGE))
			.andExpect(header("Prefer", "outlook.body-content-type=\"text\""))
			.andRespond(withSuccess("""
					{
					  "value": [{
					    "id": "BBB",
					    "subject": "Draft with no sender",
					    "receivedDateTime": "2026-11-19T08:00:00Z"
					  }]
					}
					""", MediaType.APPLICATION_JSON));

		List<Email> emails = client.getEmailsSince(MailFolder.JUNK, OffsetDateTime.parse("2026-05-23T11:00:00.123+01:00"));

		assertThat(emails).extracting(Email::id).containsExactly("AAA", "BBB");
		Email first = emails.getFirst();
		assertThat(first.folder()).isEqualTo(MailFolder.JUNK);
		assertThat(first.fromName()).isEqualTo("Farfetch Careers");
		assertThat(first.fromAddress()).isEqualTo("jobs@farfetch.com");
		assertThat(first.body()).isEqualTo("We'd like to invite you...");
		assertThat(first.receivedAt()).isEqualTo(OffsetDateTime.parse("2026-11-20T09:30:00Z"));
		assertThat(emails.get(1).fromAddress()).isNull();
		assertThat(emails.get(1).body()).isNull();
		server.verify();
	}

}
