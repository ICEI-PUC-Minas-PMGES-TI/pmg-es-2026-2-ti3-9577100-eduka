package br.pucminas.eduka.backend.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login e senha que abrem a tela de acesso de demonstracao (/login/demo).
 * Vem de {@code DEMO_ACESSO_LOGIN} e {@code DEMO_ACESSO_SENHA}, sem default
 * (ver application-demo.properties).
 */
@ConfigurationProperties(prefix = "app.demo.acesso")
public record DemoAcessoProperties(String login, String senha) {
}
