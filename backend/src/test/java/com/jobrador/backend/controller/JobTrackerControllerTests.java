package com.jobrador.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jobrador.backend.model.EmailCategory;
import com.jobrador.backend.model.EmailCorrection;
import com.jobrador.backend.model.JobDashboard;
import com.jobrador.backend.model.SyncStatus;
import com.jobrador.backend.service.JobTrackerService;

@SpringBootTest(properties = { "MS_CLIENT_SECRET=test-secret", "ANTHROPIC_API_KEY=test-key" })
@AutoConfigureMockMvc
class JobTrackerControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JobTrackerService service;

	private final JobDashboard dashboard = new JobDashboard(List.of(), List.of(new JobDashboard.Application(
			"Farfetch", "Frontend Developer", EmailCategory.INTERVIEW, null, null, "Prepare", "Invite", 2, false,
			List.of())),
			List.of(), null, 5, SyncStatus.IDLE);

	@Test
	void dashboardUsesLoggedInUserId() throws Exception {
		when(service.getDashboard("user-123")).thenReturn(dashboard);

		mockMvc.perform(get("/api/dashboard").with(oidcLogin().idToken(t -> t.subject("user-123"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.applications[0].company").value("Farfetch"))
			.andExpect(jsonPath("$.applications[0].status").value("INTERVIEW"))
			.andExpect(jsonPath("$.emailsAnalyzed").value(5));
	}

	@Test
	void syncTriggersService() throws Exception {
		when(service.sync("user-123")).thenReturn(dashboard);

		mockMvc.perform(post("/api/sync").with(oidcLogin().idToken(t -> t.subject("user-123"))))
			.andExpect(status().isOk());

		verify(service).sync("user-123");
	}

	@Test
	void correctsAnEmail() throws Exception {
		EmailCorrection fix = new EmailCorrection(EmailCategory.NOT_JOB_RELATED, null, null);
		when(service.correct("user-123", "AAA", fix)).thenReturn(Optional.of(dashboard));

		mockMvc.perform(patch("/api/emails/AAA")
				.with(oidcLogin().idToken(t -> t.subject("user-123")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"NOT_JOB_RELATED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailsAnalyzed").value(5));
	}

	@Test
	void correctingUnknownEmailIs404() throws Exception {
		when(service.correct(any(), any(), any())).thenReturn(Optional.empty());

		mockMvc.perform(patch("/api/emails/nope")
				.with(oidcLogin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"OFFER\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void requiresLogin() throws Exception {
		mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/sync")).andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/emails/AAA")).andExpect(status().isUnauthorized());
	}

}
