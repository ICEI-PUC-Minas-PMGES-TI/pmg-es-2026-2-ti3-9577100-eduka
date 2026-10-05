package br.pucminas.eduka.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
	 * Decoder usado SO pelo resource server, em cima das rotas protegidas.
	 * Alem de assinatura e expiracao, exige que o token seja um access token
	 * com perfil. O bean {@link #jwtDecoder} continua sem essa restricao porque
	 * {@link JwtService} precisa decodificar refresh tokens.
	 */
	private JwtDecoder resourceServerJwtDecoder(SecretKey jwtSecretKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(),
				new AccessTokenValidator()));
		return decoder;
	}

	/**
	 * Origens que podem chamar a API a partir do browser. Em dev e o Vite
	 * (localhost:5173); em prod o Nginx serve front e API na mesma origem, entao
	 * a lista fica vazia e nenhuma origem externa e aceita.
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	/**
	 * 401 e 403 sao entregues ao {@code ApiExceptionHandler}, para que toda
	 * resposta de erro da API tenha o mesmo formato (RFC 9457 / ProblemDetail),
	 * venha de filtro de seguranca ou de controller.
	 */
	@Bean
	public AuthenticationEntryPoint authenticationEntryPoint(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		return (request, response, ex) -> resolver.resolveException(request, response, null, ex);
	}

	@Bean
	public AccessDeniedHandler accessDeniedHandler(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		return (request, response, ex) -> resolver.resolveException(request, response, null, ex);
	}

	/**
	 * API stateless autenticada por JWT (RNF01/RNF02). Sem sessao nem cookie,
	 * CSRF nao se aplica. Apenas o health check, o preflight de CORS, o login,
	 * a troca da senha provisoria e o Swagger ficam publicos; todo o resto exige um access
	 * token valido, cujo perfil vira ROLE_* para {@code @PreAuthorize}.
	 *
	 * <p>
	 * Os modulos de {@link RotasPorPerfil} sao barrados aqui por perfil. Rotas
	 * fora deles so exigem autenticacao, e o controller precisa de
	 * {@code @PreAuthorize}.
	 * </p>
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, SecretKey jwtSecretKey,
			AuthenticationEntryPoint authenticationEntryPoint,
			AccessDeniedHandler accessDeniedHandler) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> {
					authorize
							.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
							.requestMatchers("/health", "/login", "/primeiro-acesso").permitAll()
							// Swagger (OpenApiConfig). Em prod o springdoc fica desligado e estas rotas dao 404.
							.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
					RotasPorPerfil.aplicar(authorize);
					authorize.anyRequest().authenticated();
				})
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt
								.decoder(resourceServerJwtDecoder(jwtSecretKey))
								.jwtAuthenticationConverter(new PerfilAuthenticationConverter()))
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.build();
	}
}
