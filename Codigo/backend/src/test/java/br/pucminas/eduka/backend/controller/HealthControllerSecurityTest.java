package br.pucminas.eduka.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import br.pucminas.eduka.backend.security.JwtConfig;

class HealthControllerSecurityTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(JwtConfig.class, HealthController.class)
			.withPropertyValues(
					"jwt.secret=test-secret-key-with-at-least-32-bytes!!",
					"jwt.access-expiration-minutes=60",
					"jwt.refresh-expiration-days=30");

	@Test
	void securityFilterChainBeanExists() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(SecurityFilterChain.class);
		});
	}
}
