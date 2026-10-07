package br.pucminas.eduka.backend.security;

import static br.pucminas.eduka.backend.domain.PerfilUsuario.ALUNO;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.FINANCEIRO;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.MASTER;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.PROFESSOR;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.SECRETARIA;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.SUPER_ADMIN;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import br.pucminas.eduka.backend.domain.PerfilUsuario;

/**
 * Matriz de acesso da API: quais perfis entram em cada modulo. E a "rota-mae"
 * de cada area; os endpoints (subrotas) vao sendo criados dentro dos prefixos
 * daqui e herdam o bloqueio sem precisar lembrar de nada.
 *
 * <p>
 * Aplicada no filter chain ({@link JwtConfig}), antes de chegar ao controller.
 * O refinamento por acao (quem le x quem escreve) fica no controller, com
 * {@code @PreAuthorize} e as expressoes de {@link Perfis}.
 * </p>
 *
 * <p>
 * {@code SUPER_ADMIN} administra a plataforma: enxerga o cadastro das escolas
 * e os usuarios vinculados a elas, mas nao entra nos dados de negocio de
 * nenhuma escola (academico, financeiro, relatorios).
 * </p>
 *
 * <p>
 * Prefixos sem controller ainda ja estao aqui de proposito: quando o endpoint
 * surgir, nasce protegido. Para abrir um modulo novo, acrescente uma linha.
 * </p>
 */
public enum RotasPorPerfil {

    PLATAFORMA(List.of("/escolas", "/planos"),
            SUPER_ADMIN),

    GESTAO_ESCOLA(List.of("/config-pagamento", "/auditoria"),
            MASTER),

    /** Secretaria entra para criar login de professor e aluno (ver {@link HierarquiaPerfis}). */
    USUARIOS(List.of("/usuarios"),
            SUPER_ADMIN, MASTER, SECRETARIA),

    ACADEMICO(List.of("/alunos", "/responsaveis", "/professores", "/cursos", "/disciplinas",
            "/periodos-letivos", "/turmas", "/matriculas", "/academico"),
            MASTER, SECRETARIA, FINANCEIRO, PROFESSOR),

    FINANCEIRO_ESCOLA(List.of("/cobrancas", "/boletos", "/pagamentos", "/transacoes-pagamento",
            "/despesas", "/categorias-despesa"),
            MASTER, FINANCEIRO),

    RELATORIOS(List.of("/dashboard", "/relatorios"),
            MASTER, SECRETARIA, FINANCEIRO),

    /** Area pessoal: "minhas turmas", "meus diarios". */
    PORTAL_PROFESSOR(List.of("/portal/professor"),
            PROFESSOR),

    /** Area pessoal: "minhas cobrancas", "meus dados". */
    PORTAL_ALUNO(List.of("/portal/aluno"),
            ALUNO);

    private final List<String> prefixos;
    private final Set<PerfilUsuario> perfis;

    RotasPorPerfil(List<String> prefixos, PerfilUsuario... perfis) {
        this.prefixos = prefixos;
        this.perfis = EnumSet.copyOf(Arrays.asList(perfis));
    }

    public List<String> prefixos() {
        return prefixos;
    }

    public Set<PerfilUsuario> perfis() {
        return perfis;
    }

    /**
     * Registra cada modulo no {@code authorizeHttpRequests}. {@code /x/**}
     * casa tambem com {@code /x}, entao a raiz do modulo fica coberta.
     */
    static void aplicar(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorize) {
        for (RotasPorPerfil modulo : values()) {
            String[] padroes = modulo.prefixos.stream().map(p -> p + "/**").toArray(String[]::new);
            String[] roles = modulo.perfis.stream().map(Enum::name).toArray(String[]::new);
            authorize.requestMatchers(padroes).hasAnyRole(roles);
        }
    }
}
