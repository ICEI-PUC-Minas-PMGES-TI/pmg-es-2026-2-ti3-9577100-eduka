package br.pucminas.eduka.backend.openapi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
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
 * Os erros comuns (401 sem token, 403 de perfil, 400 de corpo invalido) entram
 * sozinhos em cada rota pelo {@link #respostasDeErro()}; cada controller
 * declara com {@code @ApiResponse} so os erros proprios da rota, com o
 * {@code codigo} que a tela trata. Toda resposta de erro aponta para o
 * esquema {@value #ERRO}. Rota que declara algum {@code @ApiResponse} declara
 * tambem o 200 (ou usa {@code @ResponseStatus}): sem isso, o springdoc nao
 * gera a resposta de sucesso.
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

    /** Esquema do corpo de erro (ProblemDetail do ApiExceptionHandler). */
    static final String ERRO = "Erro";

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

                                Erros seguem o formato ProblemDetail (RFC 9457), no esquema `Erro`. \
                                Quando a tela precisa distinguir um caso (ex.: `COBRANCA_JA_PAGA`), \
                                o corpo traz o campo `codigo`; as respostas de cada rota listam os dela."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas(ERRO, esquemaDeErro()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * Completa as respostas de cada rota: 401 nas que exigem token, 403 nas
     * restritas por perfil, 400 nas que recebem corpo, e o esquema
     * {@value #ERRO} em toda resposta de erro declarada sem corpo. O que o
     * controller ja declarou para um codigo prevalece.
     */
    @Bean
    public OperationCustomizer respostasDeErro() {
        return (operation, handlerMethod) -> {
            ApiResponses respostas = operation.getResponses();
            if (!publica(handlerMethod)) {
                respostas.putIfAbsent("401", new ApiResponse().description("Sem token, ou token vencido ou invalido."));
            }
            if (anotada(handlerMethod, PreAuthorize.class)) {
                respostas.putIfAbsent("403", new ApiResponse().description("Perfil sem permissao para esta operacao."));
            }
            if (recebeCorpo(handlerMethod.getMethod())) {
                respostas.putIfAbsent("400",
                        new ApiResponse().description("Corpo invalido: campo obrigatorio ausente ou fora do formato."));
            }
            // O springdoc preenche a resposta declarada sem corpo com o retorno do metodo (o de sucesso);
            // erro sempre volta como ProblemDetail, pelo ApiExceptionHandler.
            respostas.forEach((codigo, resposta) -> {
                if (codigo.startsWith("4") || codigo.startsWith("5")) {
                    resposta.setContent(corpoDeErro());
                }
            });
            operation.setResponses(emOrdem(respostas));
            return operation;
        };
    }

    private static Schema<?> esquemaDeErro() {
        return new ObjectSchema()
                .description("Erro no formato ProblemDetail (RFC 9457).")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().description("Nome do status HTTP").example("Conflict"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema().description("Mensagem que a tela pode mostrar")
                        .example("Cobranca ja paga"))
                .addProperty("instance", new StringSchema().description("Rota que falhou")
                        .example("/cobrancas/3fa85f64-5717-4562-b3fc-2c963f66afa6/cancelar"))
                .addProperty("codigo", new StringSchema()
                        .description("Codigo estavel para a tela tratar o caso; so nos erros que listam um")
                        .example("COBRANCA_JA_PAGA"));
    }

    private static Content corpoDeErro() {
        return new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                new io.swagger.v3.oas.models.media.MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + ERRO)));
    }

    /** Rota publica: o controller ou o metodo anulam o token com {@code @SecurityRequirements} vazio. */
    private static boolean publica(HandlerMethod handlerMethod) {
        return anotada(handlerMethod, SecurityRequirements.class);
    }

    private static boolean anotada(HandlerMethod handlerMethod, Class<? extends Annotation> tipo) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), tipo)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), tipo);
    }

    private static boolean recebeCorpo(Method metodo) {
        return Arrays.stream(metodo.getParameters()).anyMatch(p -> p.isAnnotationPresent(RequestBody.class));
    }

    /** Respostas pelo codigo (200, 201, 400, 401...), para a tela do Swagger listar em ordem. */
    private static ApiResponses emOrdem(ApiResponses respostas) {
        ApiResponses ordenadas = new ApiResponses();
        respostas.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entrada -> ordenadas.addApiResponse(entrada.getKey(), entrada.getValue()));
        return ordenadas;
    }
}
