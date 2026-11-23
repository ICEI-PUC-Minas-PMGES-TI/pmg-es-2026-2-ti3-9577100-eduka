package br.pucminas.eduka.backend.security;

import static br.pucminas.eduka.backend.domain.acesso.PerfilUsuario.ALUNO;
import static br.pucminas.eduka.backend.domain.acesso.PerfilUsuario.FINANCEIRO;
import static br.pucminas.eduka.backend.domain.acesso.PerfilUsuario.PROFESSOR;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.util.AntPathMatcher;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.escola.ModuloEscola;

/**
 * O que cada modulo contratado (ver {@link ModuloEscola}) libera na API. Sem
 * o modulo, a escola perde as rotas dele e o perfil que so existe para ele
 * nao entra: o FINANCEIRO sem a gestao financeira, o aluno sem o portal do
 * aluno, o professor sem o portal do professor.
 *
 * <p>
 * Aplicado por {@link ModuloContratadoFilter} a cada requisicao e pelo login
 * ({@code AuthService}). A matriz por perfil ({@link RotasPorPerfil}) continua
 * valendo: o modulo so restringe, nunca libera.
 * </p>
 *
 * <p>
 * {@code SECRETARIA_VIRTUAL} e {@code BIBLIOTECA_DIGITAL} ainda nao tem
 * funcionalidade no Eduka, por isso nao aparecem aqui. Faturas da plataforma
 * e assinatura ({@code /config-pagamento/faturas}, {@code /assinatura}) nao
 * dependem de modulo: a escola sempre consegue pagar o Eduka.
 * </p>
 */
public enum RotasPorModulo {

    GESTAO_FINANCEIRA(ModuloEscola.GESTAO_FINANCEIRA,
            List.of("/cobrancas/**", "/boletos/**", "/pagamentos/**", "/transacoes-pagamento/**",
                    "/despesas/**", "/categorias-despesa/**", "/relatorios/financeiro/**",
                    "/dashboard/financeiro/**", "/alunos/*/financeiro/**", "/config-pagamento/conta/**",
                    "/portal/aluno/financeiro/**", "/portal/aluno/cobrancas/**"),
            FINANCEIRO),

    PORTAL_ALUNO(ModuloEscola.PORTAL_ALUNO, List.of("/portal/aluno/**"), ALUNO),

    PORTAL_PROFESSOR(ModuloEscola.PORTAL_PROFESSOR, List.of("/portal/professor/**"), PROFESSOR),

    /** Aulas e chamada do professor. */
    CONTROLE_FREQUENCIA(ModuloEscola.CONTROLE_FREQUENCIA, List.of("/portal/professor/aulas/**"));

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final ModuloEscola modulo;
    private final List<String> padroes;
    private final Set<PerfilUsuario> perfis;

    RotasPorModulo(ModuloEscola modulo, List<String> padroes, PerfilUsuario... perfis) {
        this.modulo = modulo;
        this.padroes = padroes;
        this.perfis = perfis.length == 0 ? EnumSet.noneOf(PerfilUsuario.class)
                : EnumSet.copyOf(Arrays.asList(perfis));
    }

    public ModuloEscola modulo() {
        return modulo;
    }

    /** O modulo sem o qual o perfil nao entra no sistema, se houver. */
    public static Optional<ModuloEscola> exigidoPeloPerfil(PerfilUsuario perfil) {
        return Arrays.stream(values())
                .filter(r -> r.perfis.contains(perfil))
                .map(RotasPorModulo::modulo)
                .findFirst();
    }

    /** Os modulos que a rota exige (vazio para rota que nao depende de modulo). */
    public static Set<ModuloEscola> exigidosPelaRota(String caminho) {
        Set<ModuloEscola> exigidos = EnumSet.noneOf(ModuloEscola.class);
        for (RotasPorModulo rotas : values()) {
            if (rotas.padroes.stream().anyMatch(p -> MATCHER.match(p, caminho))) {
                exigidos.add(rotas.modulo);
            }
        }
        return exigidos;
    }
}
