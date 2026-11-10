package br.pucminas.eduka.backend.security;

import static br.pucminas.eduka.backend.domain.PerfilUsuario.ALUNO;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.FINANCEIRO;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.MASTER;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.PROFESSOR;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.SECRETARIA;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.SUPER_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.PerfilUsuario;

/**
 * Contrato de acesso da API (RNF02): cada endpoint, com os perfis que entram
 * nele. A lista {@link #MATRIZ} e escrita a mao de proposito, para nao repetir
 * o que o codigo diz: mudar quem acessa o que exige mudar aqui tambem, e
 * endpoint novo fora da lista quebra o teste.
 *
 * <p>
 * O que o codigo libera sai das duas camadas de producao: {@link RotasPorPerfil}
 * e o {@code @PreAuthorize}, avaliado pelo mesmo gerenciador que o Spring usa
 * nos controllers. Nao sobe contexto nem faz requisicao, entao cobre tambem as
 * rotas com corpo, em que o {@code 400} da validacao viria antes do {@code 403}.
 * Que as duas camadas de fato barram no filter chain esta provado em
 * {@link RotasPorPerfilIntegrationTest} e nos {@code *ControllerSecurityTest}.
 * </p>
 */
class MatrizDeAcessoTest {

    private static final Set<PerfilUsuario> PLATAFORMA = EnumSet.of(SUPER_ADMIN);
    private static final Set<PerfilUsuario> GESTAO = EnumSet.of(MASTER);
    private static final Set<PerfilUsuario> CADASTRO = EnumSet.of(MASTER, SECRETARIA);
    private static final Set<PerfilUsuario> FINANCAS = EnumSet.of(MASTER, FINANCEIRO);
    private static final Set<PerfilUsuario> ADMINISTRATIVOS = EnumSet.of(MASTER, SECRETARIA, FINANCEIRO);
    private static final Set<PerfilUsuario> EQUIPE = EnumSet.of(MASTER, SECRETARIA, FINANCEIRO, PROFESSOR);
    private static final Set<PerfilUsuario> SO_PROFESSOR = EnumSet.of(PROFESSOR);
    private static final Set<PerfilUsuario> SO_ALUNO = EnumSet.of(ALUNO);

    /** Sem token: conferem credencial, token do provedor ou o acesso de demonstracao. */
    private static final Set<String> PUBLICAS = Set.of(
            "GET /health",
            "POST /login",
            "POST /login/codigo",
            "POST /login/codigo/reenvio",
            "POST /primeiro-acesso",
            "POST /webhooks/asaas",
            "POST /demo/contas",
            "POST /demo/entrar");

    private static final Map<String, Set<PerfilUsuario>> MATRIZ = matriz();

    private static final List<Endpoint> ENDPOINTS = encontrarEndpoints();

    private static Map<String, Set<PerfilUsuario>> matriz() {
        Map<String, Set<PerfilUsuario>> matriz = new HashMap<>();

        libera(matriz, PLATAFORMA,
                "GET /escolas",
                "POST /escolas",
                "GET /escolas/{id}",
                "GET /escolas/uso",
                "PUT /escolas/{id}",
                "PATCH /escolas/{id}/ativar",
                "PATCH /escolas/{id}/suspender",
                "GET /escolas/faturas",
                "GET /escolas/{escolaId}/faturas",
                "POST /escolas/{escolaId}/faturas",
                "GET /escolas/{escolaId}/faturas/pagina",
                "PATCH /escolas/{escolaId}/faturas/{id}/cancelar",
                "GET /escolas/{escolaId}/faturas/{id}/comprovante");

        libera(matriz, GESTAO,
                "GET /auditoria/pagina",
                "GET /auditoria/autores",
                "GET /dashboard/escola",
                "GET /config-pagamento/assinatura",
                "GET /config-pagamento/assinatura/suspensao",
                "GET /config-pagamento/conta",
                "POST /config-pagamento/conta/ativar",
                "POST /config-pagamento/conta/sincronizar",
                "POST /config-pagamento/conta/sandbox/aprovar",
                "GET /config-pagamento/faturas",
                "GET /config-pagamento/faturas/pagina",
                "GET /config-pagamento/faturas/{id}",
                "POST /config-pagamento/faturas/{id}/pagar",
                "GET /config-pagamento/faturas/{id}/comprovante",
                "POST /config-pagamento/faturas/{id}/sandbox/pagar",
                "GET /usuarios/equipe",
                "POST /usuarios/equipe",
                "GET /usuarios/equipe/{id}",
                "PUT /usuarios/equipe/{id}",
                "POST /usuarios/equipe/{id}/primeiro-acesso",
                "PATCH /usuarios/equipe/{id}/inativar",
                "PATCH /usuarios/equipe/{id}/ativar",
                // LGPD: apagar o dado pessoal nao tem volta.
                "POST /alunos/{id}/anonimizar");

        // Estrutura da escola, sem dado pessoal: quem trabalha nela le.
        libera(matriz, EQUIPE,
                "GET /academico/secretaria/periodos-letivos",
                "GET /academico/secretaria/cursos",
                "GET /academico/secretaria/disciplinas",
                "GET /academico/secretaria/turmas",
                "GET /academico/secretaria/turmas/pagina");

        // Alunos e matriculas da escola inteira: professor so ve os das proprias turmas, pelo portal.
        libera(matriz, ADMINISTRATIVOS,
                "GET /alunos",
                "GET /alunos/pagina",
                "GET /alunos/{id}",
                "GET /matriculas",
                "GET /matriculas/pagina",
                "GET /matriculas/{id}",
                "GET /responsaveis",
                "POST /responsaveis",
                "GET /responsaveis/{id}",
                "PUT /responsaveis/{id}",
                "DELETE /responsaveis/{id}",
                "GET /alunos/{alunoId}/responsaveis",
                "POST /alunos/{alunoId}/responsaveis",
                "PUT /alunos/{alunoId}/responsaveis/{responsavelId}",
                "DELETE /alunos/{alunoId}/responsaveis/{responsavelId}");

        libera(matriz, CADASTRO,
                "GET /dashboard/secretaria",
                "POST /alunos",
                "PUT /alunos/{id}",
                "POST /alunos/{id}/primeiro-acesso",
                "GET /alunos/{id}/dados-pessoais",
                "GET /alunos/correcoes",
                "PATCH /alunos/correcoes/{id}/atender",
                "POST /matriculas",
                "PATCH /matriculas/{id}",
                "GET /professores",
                "POST /professores",
                "GET /professores/pagina",
                "GET /professores/{id}",
                "PUT /professores/{id}",
                "DELETE /professores/{id}",
                "PATCH /professores/{id}/ativar",
                "PATCH /professores/{id}/inativar",
                "POST /professores/{id}/primeiro-acesso",
                "GET /professores/{professorId}/alocacoes",
                "POST /professores/{professorId}/alocacoes",
                "DELETE /professores/{professorId}/alocacoes/{alocacaoId}",
                "GET /turmas/{turmaId}/alocacoes",
                "GET /turmas/{turmaId}/horario",
                "POST /turmas/{turmaId}/horario",
                "PUT /turmas/{turmaId}/horario/{horarioId}",
                "DELETE /turmas/{turmaId}/horario/{horarioId}",
                "POST /academico/secretaria/periodos-letivos",
                "PUT /academico/secretaria/periodos-letivos/{id}",
                "DELETE /academico/secretaria/periodos-letivos/{id}",
                "PATCH /academico/secretaria/periodos-letivos/{id}/encerrar",
                "PATCH /academico/secretaria/periodos-letivos/{id}/abrir-matriculas",
                "POST /academico/secretaria/cursos",
                "PUT /academico/secretaria/cursos/{id}",
                "DELETE /academico/secretaria/cursos/{id}",
                "POST /academico/secretaria/disciplinas",
                "PUT /academico/secretaria/disciplinas/{id}",
                "DELETE /academico/secretaria/disciplinas/{id}",
                "POST /academico/secretaria/turmas",
                "PUT /academico/secretaria/turmas/{id}",
                "DELETE /academico/secretaria/turmas/{id}");

        libera(matriz, FINANCAS,
                "GET /dashboard/financeiro",
                "GET /dashboard/financeiro/movimento",
                "GET /relatorios/financeiro",
                "GET /relatorios/financeiro/exportar",
                "GET /alunos/{alunoId}/financeiro",
                "GET /alunos/{alunoId}/financeiro/risco",
                "GET /cobrancas",
                "POST /cobrancas",
                "GET /cobrancas/pagina",
                "GET /cobrancas/risco-atraso",
                "GET /cobrancas/{id}",
                "PUT /cobrancas/{id}",
                "GET /cobrancas/{id}/comprovante",
                "PATCH /cobrancas/{id}/cancelar",
                "PATCH /cobrancas/{id}/pagar",
                "POST /cobrancas/{id}/sandbox/confirmar-pagamento",
                "GET /despesas",
                "POST /despesas",
                "GET /despesas/pagina",
                "GET /despesas/{id}",
                "PUT /despesas/{id}",
                "PATCH /despesas/{id}",
                "GET /categorias-despesa",
                "POST /categorias-despesa");

        libera(matriz, SO_PROFESSOR,
                "GET /portal/professor/dashboard",
                "GET /portal/professor/turmas",
                "GET /portal/professor/turmas/{turmaId}/alunos",
                "GET /portal/professor/aulas",
                "POST /portal/professor/aulas",
                "GET /portal/professor/aulas/chamada",
                "GET /portal/professor/aulas/{id}",
                "PUT /portal/professor/aulas/{id}",
                "DELETE /portal/professor/aulas/{id}",
                "GET /portal/professor/avaliacoes",
                "POST /portal/professor/avaliacoes",
                "GET /portal/professor/avaliacoes/{id}",
                "PUT /portal/professor/avaliacoes/{id}",
                "DELETE /portal/professor/avaliacoes/{id}",
                "GET /portal/professor/avaliacoes/{id}/notas",
                "PUT /portal/professor/avaliacoes/{id}/notas");

        libera(matriz, SO_ALUNO,
                "GET /portal/aluno/me",
                "GET /portal/aluno/turmas",
                "GET /portal/aluno/boletim",
                "GET /portal/aluno/avaliacoes",
                "GET /portal/aluno/financeiro",
                "GET /portal/aluno/cobrancas/{id}",
                "GET /portal/aluno/cobrancas/{id}/boleto",
                "GET /portal/aluno/cobrancas/{id}/comprovante",
                "POST /portal/aluno/cobrancas/{id}/pagar",
                "POST /portal/aluno/cobrancas/{id}/sandbox/pagar",
                "POST /portal/aluno/correcoes",
                "GET /portal/aluno/correcoes/aberta");

        return matriz;
    }

    private static void libera(Map<String, Set<PerfilUsuario>> matriz, Set<PerfilUsuario> perfis, String... rotas) {
        for (String rota : rotas) {
            assertThat(matriz.put(rota, perfis)).as("rota repetida na matriz: %s", rota).isNull();
        }
    }

    @Test
    void todoEndpointDaApiEstaNaMatriz() {
        List<String> esperadas = new ArrayList<>(MATRIZ.keySet());
        esperadas.addAll(PUBLICAS);

        assertThat(ENDPOINTS).extracting(Endpoint::rota)
                .as("endpoint novo precisa entrar na matriz, e endpoint removido precisa sair")
                .containsExactlyInAnyOrderElementsOf(esperadas);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PerfilUsuario.class)
    void cadaPerfilEntraSoNoQueAMatrizLibera(PerfilUsuario perfil) {
        List<String> esperadas = MATRIZ.entrySet().stream()
                .filter(linha -> linha.getValue().contains(perfil))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(ENDPOINTS).filteredOn(Endpoint::protegido).filteredOn(e -> e.libera(perfil))
                .extracting(Endpoint::rota)
                .as("rotas que o codigo libera para %s", perfil)
                .containsExactlyInAnyOrderElementsOf(esperadas);
    }

    @Test
    void soAsRotasPublicasFicamSemPreAuthorize() {
        // Rota sem @PreAuthorize e fora dos modulos ficaria aberta a qualquer perfil autenticado.
        assertThat(ENDPOINTS).filteredOn(endpoint -> !endpoint.protegido()).extracting(Endpoint::rota)
                .containsExactlyInAnyOrderElementsOf(PUBLICAS);
    }

    @Test
    void rotaProtegidaFicaNumModuloERotaPublicaFora() {
        assertThat(ENDPOINTS).filteredOn(Endpoint::protegido).filteredOn(endpoint -> !endpoint.temRotaMae())
                .extracting(Endpoint::rota).as("protegidas fora de RotasPorPerfil").isEmpty();
        // A rota-mae barraria quem ainda nao tem token, mesmo com permitAll.
        assertThat(ENDPOINTS).filteredOn(endpoint -> !endpoint.protegido()).filteredOn(Endpoint::temRotaMae)
                .extracting(Endpoint::rota).as("publicas dentro de RotasPorPerfil").isEmpty();
    }

    @Test
    void preAuthorizeNaoLiberaPerfilQueARotaMaeBarra() {
        // Regra de Perfis: permissao que o filtro barra antes e letra morta, e engana quem le o controller.
        List<String> letraMorta = new ArrayList<>();
        for (Endpoint endpoint : ENDPOINTS) {
            for (PerfilUsuario perfil : PerfilUsuario.values()) {
                if (endpoint.protegido() && endpoint.preAuthorizeLibera(perfil) && !endpoint.rotaMaeLibera(perfil)) {
                    letraMorta.add(endpoint.rota() + " para " + perfil);
                }
            }
        }

        assertThat(letraMorta).isEmpty();
    }

    /**
     * Os controllers em {@code main}, inclusive os dos perfis {@code dev} e
     * {@code demo}: o scanner respeita {@code @Profile}, e sem ativa-los eles
     * sumiriam daqui em silencio.
     */
    private static List<Endpoint> encontrarEndpoints() {
        StandardEnvironment ambiente = new StandardEnvironment();
        ambiente.setActiveProfiles("dev", "demo");
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false,
                ambiente);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Endpoint> endpoints = new ArrayList<>();
        for (BeanDefinition definicao : scanner.findCandidateComponents("br.pucminas.eduka.backend")) {
            Class<?> controller = carregar(definicao.getBeanClassName());
            if (!controller.getProtectionDomain().getCodeSource().getLocation().toString().contains("test-classes")) {
                endpoints.addAll(endpointsDe(controller));
            }
        }
        return endpoints;
    }

    private static List<Endpoint> endpointsDe(Class<?> controller) {
        List<Endpoint> endpoints = new ArrayList<>();
        RequestMapping daClasse = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        for (Method metodo : controller.getDeclaredMethods()) {
            RequestMapping doMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
            if (doMetodo == null) {
                continue;
            }
            for (String base : caminhos(daClasse)) {
                for (String caminho : caminhos(doMetodo)) {
                    for (RequestMethod verbo : doMetodo.method()) {
                        endpoints.add(new Endpoint(verbo + " " + base + caminho, controller, metodo));
                    }
                }
            }
        }
        return endpoints;
    }

    private static String[] caminhos(RequestMapping mapeamento) {
        return mapeamento == null || mapeamento.path().length == 0 ? new String[] { "" } : mapeamento.path();
    }

    private static Class<?> carregar(String nome) {
        try {
            return Class.forName(nome);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Endpoint(String rota, Class<?> controller, Method metodo) {

        @Override
        public String toString() {
            return rota;
        }

        boolean protegido() {
            return avaliarPreAuthorize(ALUNO) != null;
        }

        /** O que vale em producao: precisa passar pelas duas camadas. */
        boolean libera(PerfilUsuario perfil) {
            return preAuthorizeLibera(perfil) && rotaMaeLibera(perfil);
        }

        boolean preAuthorizeLibera(PerfilUsuario perfil) {
            AuthorizationResult resultado = avaliarPreAuthorize(perfil);
            return resultado != null && resultado.isGranted();
        }

        boolean rotaMaeLibera(PerfilUsuario perfil) {
            return moduloDaRota().stream().allMatch(modulo -> modulo.perfis().contains(perfil));
        }

        boolean temRotaMae() {
            return !moduloDaRota().isEmpty();
        }

        private List<RotasPorPerfil> moduloDaRota() {
            String caminho = rota.substring(rota.indexOf(' ') + 1);
            return Arrays.stream(RotasPorPerfil.values())
                    .filter(modulo -> modulo.prefixos().stream()
                            .anyMatch(prefixo -> caminho.equals(prefixo) || caminho.startsWith(prefixo + "/")))
                    .toList();
        }

        private AuthorizationResult avaliarPreAuthorize(PerfilUsuario perfil) {
            Authentication autenticacao = new TestingAuthenticationToken("usuario", null, "ROLE_" + perfil.name());
            Object controllerSemDependencias = new SpringObjenesis().newInstance(controller);
            return new PreAuthorizeAuthorizationManager().authorize(() -> autenticacao,
                    new SimpleMethodInvocation(controllerSemDependencias, metodo));
        }
    }
}
