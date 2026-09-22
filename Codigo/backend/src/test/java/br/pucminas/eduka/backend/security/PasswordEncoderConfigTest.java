package br.pucminas.eduka.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void exposesSinglePasswordEncoderBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(PasswordEncoderConfig.class)
                .run(context -> assertThat(context).hasSingleBean(PasswordEncoder.class));
    }

    @Test
    void hashUsesBcryptWithStrength12AndAlgorithmPrefix() {
        String hash = encoder.encode("senha-secreta");

        assertThat(hash).startsWith("{bcrypt}$2a$12$");
    }

    @Test
    void hashNeverContainsThePlainPassword() {
        String senha = "senha-secreta";

        assertThat(encoder.encode(senha)).doesNotContain(senha);
    }

    @Test
    void sameSenhaProducesDifferentHashesBecauseOfSalt() {
        String primeiro = encoder.encode("senha-secreta");
        String segundo = encoder.encode("senha-secreta");

        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(encoder.matches("senha-secreta", primeiro)).isTrue();
        assertThat(encoder.matches("senha-secreta", segundo)).isTrue();
    }

    @Test
    void matchesRejectsWrongPassword() {
        String hash = encoder.encode("senha-secreta");

        assertThat(encoder.matches("senha-errada", hash)).isFalse();
        assertThat(encoder.matches("senha-secreta ", hash)).isFalse();
    }

    @Test
    void currentHashDoesNotRequireUpgrade() {
        String hash = encoder.encode("senha-secreta");

        assertThat(encoder.upgradeEncoding(hash)).isFalse();
    }

    @Test
    void hashWithLowerCostIsFlaggedForUpgrade() {
        // Simula uma senha gravada com custo 10 (o padrao do Spring) antes da
        // mudanca para 12: deve continuar validando e ser marcada para regravacao.
        String hashCusto10 = "{bcrypt}" + new BCryptPasswordEncoder(10).encode("senha-secreta");

        assertThat(encoder.matches("senha-secreta", hashCusto10)).isTrue();
        assertThat(encoder.upgradeEncoding(hashCusto10)).isTrue();
    }
}
