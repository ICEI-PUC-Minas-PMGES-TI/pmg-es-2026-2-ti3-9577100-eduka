package br.pucminas.eduka.backend.security;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hashing de senhas (RNF04).
 *
 * <p>
 * O hash e gravado com o prefixo do algoritmo, no formato
 * {@code {bcrypt}$2a$12$...}. Isso permite trocar o algoritmo padrao no futuro
 * (por exemplo para Argon2id) sem invalidar as senhas ja cadastradas: hashes
 * antigos continuam sendo verificados pelo encoder correspondente e sao
 * re-gerados no proximo login bem-sucedido.
 * </p>
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Custo do BCrypt. Cada incremento dobra o tempo de calculo; 12 fica na casa
     * das centenas de milissegundos, que e a recomendacao atual da OWASP.
     */
    static final int BCRYPT_STRENGTH = 12;

    static final String ENCODER_PADRAO = "bcrypt";

    /**
     * Construido manualmente em vez de
     * {@code PasswordEncoderFactories.createDelegatingPasswordEncoder()} porque a
     * factory fixa o custo do BCrypt em 10 e nao aceita parametro.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of(
                ENCODER_PADRAO, new BCryptPasswordEncoder(BCRYPT_STRENGTH));
        return new DelegatingPasswordEncoder(ENCODER_PADRAO, encoders);
    }
}
