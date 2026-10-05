package br.pucminas.eduka.backend.openapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentacao OpenAPI da API, servida pelo springdoc:
 * <ul>
 * <li>{@code /swagger-ui.html}: tela interativa para testar as rotas;</li>
 * <li>{@code /v3/api-docs}: o contrato em JSON (importavel no Postman/Insomnia).</li>
 * </ul>
 *
 * <p>
 * Toda rota exige o access token por padrao (requisito global abaixo). Para
 * testar: chamar {@code POST /login}, copiar o {@code accessToken} da resposta
 * e colar no botao "Authorize" da tela. As rotas publicas (login, primeiro
 * acesso, health) anulam o requisito com {@code @SecurityRequirements}.
 * </p>
 *
 * <p>
 * Desligado no perfil prod (ver application-prod.properties): o contrato
 * completo da API nao fica exposto em producao.
 * </p>
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI eduKaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Eduka API")
                        .version("v1")
                        .description("""
                                API do sistema de gestao escolar Eduka.

                                Autenticacao: JWT no header `Authorization: Bearer <token>`. \
                                Obtenha o token em `POST /login` e use o botao **Authorize**.

                                O tenant (escola) e sempre o do token: nenhuma rota recebe \
                                `escolaId` na URL. Cada operacao indica os perfis que podem chama-la; \
                                perfil sem permissao recebe 403.

                                Erros seguem o formato ProblemDetail (RFC 9457)."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
