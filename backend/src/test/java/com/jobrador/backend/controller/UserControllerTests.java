package com.jobrador.backend.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "MS_CLIENT_SECRET=test-secret", "ANTHROPIC_API_KEY=test-key" })
@AutoConfigureMockMvc
class UserControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsLoggedInUser() throws Exception {
		mockMvc.perform(get("/api/me").with(oidcLogin().idToken(token -> token
				.claim("name", "Vicente Martins")
				.claim("email", "vicente@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Vicente Martins"))
			.andExpect(jsonPath("$.email").value("vicente@example.com"));
	}

	@Test
	void returns401WhenNotLoggedIn() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void redirectsToMicrosoftLogin() throws Exception {
		mockMvc.perform(get("/oauth2/authorization/microsoft"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", startsWith(
					"https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize")));
	}

	@Test
	void logoutReturns200() throws Exception {
		mockMvc.perform(post("/api/logout").with(oidcLogin()))
			.andExpect(status().isOk());
	}

}
