package br.pucminas.eduka.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
		String secret,
		long accessExpirationMinutes,
		long refreshExpirationDays) {
}
