package com.jobrador.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JobApplicationControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsMockApplications() throws Exception {
		mockMvc.perform(get("/api/applications"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(5))
			.andExpect(jsonPath("$[0].company").value("Farfetch"))
			.andExpect(jsonPath("$[0].appliedDate").value("2026-11-02"));
	}

	@Test
	void allowsCorsFromAngularDevServer() throws Exception {
		mockMvc.perform(options("/api/applications")
				.header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
	}

	@Test
	void rejectsCorsFromUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/applications")
				.header("Origin", "http://evil.example")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden());
	}

}
