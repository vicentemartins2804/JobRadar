package com.jobrador.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.jobrador.backend.model.EmailCorrection;
import com.jobrador.backend.model.JobDashboard;
import com.jobrador.backend.service.JobTrackerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobTrackerController {

	private final JobTrackerService service;

	/** Current organized view, from emails analyzed so far (no Outlook/Claude calls). */
	@GetMapping("/dashboard")
	public JobDashboard dashboard(@AuthenticationPrincipal OidcUser user) {
		return service.getDashboard(user.getSubject());
	}

	/**
	 * Reads new emails from Inbox + Junk and starts analyzing them with Claude in the background.
	 * Returns once emails are fetched; poll GET /dashboard while {@code sync.running} is true.
	 */
	@PostMapping("/sync")
	public JobDashboard sync(@AuthenticationPrincipal OidcUser user) {
		return service.sync(user.getSubject());
	}

	/** Manually fixes Claude's analysis of one email (category, company, role). */
	@PatchMapping("/emails/{emailId}")
	public JobDashboard correct(@AuthenticationPrincipal OidcUser user, @PathVariable String emailId,
			@RequestBody EmailCorrection correction) {
		return service.correct(user.getSubject(), emailId, correction)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
	}

}
