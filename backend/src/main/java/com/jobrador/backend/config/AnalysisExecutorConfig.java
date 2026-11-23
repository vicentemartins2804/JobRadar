package com.jobrador.backend.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalysisExecutorConfig {

	/** How many emails are sent to Claude at the same time (kept low to stay under API rate limits). */
	private static final int PARALLEL_ANALYSES = 5;

	@Bean(destroyMethod = "shutdownNow")
	ExecutorService emailAnalysisExecutor() {
		return Executors.newFixedThreadPool(PARALLEL_ANALYSES);
	}

}
