package com.jobrador.backend.client;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Shape of Microsoft Graph's GET /me/mailFolders/{folder}/messages response (only the fields we select).
 * {@code nextLink} is the URL of the next page, or null on the last page.
 */
record GraphMessages(List<Message> value, @JsonProperty("@odata.nextLink") String nextLink) {

	record Message(String id, String subject, From from, OffsetDateTime receivedDateTime, Body body) {
	}

	record From(EmailAddress emailAddress) {
	}

	record EmailAddress(String name, String address) {
	}

	record Body(String contentType, String content) {
	}

}
