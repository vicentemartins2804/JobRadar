package com.jobrador.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class GraphClientConfig {

	@Bean
	OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
			OAuth2AuthorizedClientRepository authorizedClients) {
		DefaultOAuth2AuthorizedClientManager manager =
				new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClients);
		// refreshToken() renews the access token with the refresh token (offline_access) when it expires
		manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
				.authorizationCode()
				.refreshToken()
				.build());
		return manager;
	}

	/**
	 * RestClient for Microsoft Graph that adds the logged-in user's access token
	 * ("Authorization: Bearer ...") to every request.
	 */
	@Bean
	RestClient graphRestClient(OAuth2AuthorizedClientManager authorizedClientManager) {
		OAuth2ClientHttpRequestInterceptor interceptor =
				new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
		interceptor.setClientRegistrationIdResolver(request -> "microsoft");
		return RestClient.builder()
				.baseUrl("https://graph.microsoft.com/v1.0")
				.requestInterceptor(interceptor)
				.build();
	}

}
