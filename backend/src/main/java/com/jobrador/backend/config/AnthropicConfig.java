package com.jobrador.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

@Configuration
public class AnthropicConfig {

	@Bean(destroyMethod = "close")
	AnthropicClient anthropicClient(@Value("${anthropic.api-key}") String apiKey) {
		return AnthropicOkHttpClient.builder()
				.apiKey(apiKey)
				// The first sync can send hundreds of requests; retry rate limits (429) with backoff
				.maxRetries(5)
				.build();
	}

}
