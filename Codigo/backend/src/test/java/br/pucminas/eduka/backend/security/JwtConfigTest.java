package br.pucminas.eduka.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwtConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class)
            .withPropertyValues(
                    "jwt.secret=test-secret-key-with-at-least-32-bytes!!",
                    "jwt.access-expiration-minutes=60",
                    "jwt.refresh-expiration-days=30");

    @Test
    void bindsPropertiesAndExposesBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JwtProperties.class);
            assertThat(context).hasSingleBean(JwtEncoder.class);
            assertThat(context).hasSingleBean(JwtDecoder.class);
            assertThat(context).hasSingleBean(Clock.class);

            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.secret()).isEqualTo("test-secret-key-with-at-least-32-bytes!!");
            assertThat(properties.accessExpirationMinutes()).isEqualTo(60);
            assertThat(properties.refreshExpirationDays()).isEqualTo(30);
        });
    }

    @Test
    void encoderAndDecoderRoundTripAToken() {
        contextRunner.run(context -> {
            JwtEncoder encoder = context.getBean(JwtEncoder.class);
            JwtDecoder decoder = context.getBean(JwtDecoder.class);

            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject("round-trip-check")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(60))
                    .build();
            String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

            assertThat(decoder.decode(token).getSubject()).isEqualTo("round-trip-check");
        });
    }
}
