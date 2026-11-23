package com.jobrador.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "MS_CLIENT_SECRET=test-secret", "ANTHROPIC_API_KEY=test-key" })
@AutoConfigureMockMvc
class CorsTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allowsCorsFromAngularDevServer() throws Exception {
		mockMvc.perform(options("/api/dashboard")
				.header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
			.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void rejectsCorsFromUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/dashboard")
				.header("Origin", "http://evil.example")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden());
	}

}
