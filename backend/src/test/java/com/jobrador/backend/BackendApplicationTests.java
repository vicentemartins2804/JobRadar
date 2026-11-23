package com.jobrador.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "MS_CLIENT_SECRET=test-secret", "ANTHROPIC_API_KEY=test-key" })
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
