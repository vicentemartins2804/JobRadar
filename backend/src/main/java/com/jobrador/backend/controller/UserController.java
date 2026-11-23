package com.jobrador.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobrador.backend.model.UserInfo;

@RestController
@RequestMapping("/api/me")
public class UserController {

	@GetMapping
	public UserInfo me(@AuthenticationPrincipal OidcUser user) {
		String email = user.getEmail() != null ? user.getEmail() : user.getPreferredUsername();
		return new UserInfo(user.getFullName(), email);
	}

}
