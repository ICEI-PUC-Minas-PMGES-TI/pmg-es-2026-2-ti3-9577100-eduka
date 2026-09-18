package br.pucminas.eduka.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	private static final int MIN_SECRET_BYTES = 32;

	@Bean
	public SecretKey jwtSecretKey(JwtProperties jwtProperties) {
		String secret = jwtProperties.secret();
		validateSecret(secret);
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	private void validateSecret(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("JWT_SECRET must not be null or blank");
		}
		if (secret.startsWith("${")) {
			throw new IllegalStateException("JWT_SECRET was not resolved (found unresolved placeholder)");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long for HS256");
		}
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * API stateless autenticada por JWT (RNF01/RNF02). Sem sessao nem cookie,
	 * CSRF nao se aplica. Apenas o health check e as rotas de autenticacao
	 * (login e futura recuperacao de senha) ficam publicas; todo o resto exige
	 * um access token valido.
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/health", "/auth/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
				.build();
	}
}
