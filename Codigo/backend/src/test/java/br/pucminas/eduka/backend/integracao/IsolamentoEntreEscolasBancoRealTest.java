package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * Isolamento entre escolas pela API, ponta a ponta (RNF03): token real, filter
 * chain, services, repositorios e Postgres. A escola do token nunca alcanca o
 * que e de outra, nem mesmo conhecendo os ids.
 *
 * <p>
 * Cobre em especial as entidades sem {@code @TenantId} (turma, curso,
 * disciplina, periodo letivo, usuario), que dependem do filtro explicito de
 * cada consulta. Cada negativa tem o controle: o dono do dado o alcanca pela
 * mesma URL, entao o 404 nao vem de rota errada nem de cenario vazio.
 * </p>
 */
@AutoConfigureMockMvc
class IsolamentoEntreEscolasBancoRealTest extends BancoRealTest {

    private static final String COM_NOME = "{\"nome\":\"Nome trocado\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String masterDaEscolaA;
    private String masterDaEscolaB;

    /** Marcas unicas dos dados da escola B: nao podem aparecer em nada que a escola A leia. */
    private String nomeDoAluno;
    private String nomeDaTurma;
    private String nomeDaDisciplina;
    private String nomeDoProfessor;
    private String nomeDaSecretaria;

    private UUID escolaB;
    private UUID alunoB;
    private UUID matriculaB;
    private UUID professorB;
    private UUID secretariaB;
    private UUID cobrancaPagaB;
    private UUID cobrancaAbertaB;
    private UUID despesaB;
    private UUID faturaB;
    private long turmaB;
    private long cursoB;
    private long periodoB;
    private long disciplinaB;

    @BeforeEach
    void duasEscolas() {
        UUID escolaA = dados.escola();
        escolaB = dados.escola();
        masterDaEscolaA = jwtService.generateAccessToken(UUID.randomUUID(), "MASTER", escolaA);
        masterDaEscolaB = jwtService.generateAccessToken(UUID.randomUUID(), "MASTER", escolaB);

        nomeDoAluno = "Aluno " + DadosDeTeste.unico();
        nomeDaTurma = "Turma " + DadosDeTeste.unico();
        nomeDaDisciplina = "Disciplina " + DadosDeTeste.unico();
        nomeDoProfessor = "Professor " + DadosDeTeste.unico();
        nomeDaSecretaria = "Secretaria " + DadosDeTeste.unico();

        alunoB = dados.aluno(escolaB, nomeDoAluno);
        matriculaB = dados.matriculaNoCurso(escolaB, alunoB, "Curso " + DadosDeTeste.unico());
        professorB = dados.professor(escolaB, nomeDoProfessor, LocalDate.of(2026, 1, 1), true);
        secretariaB = dados.usuario(escolaB, "SECRETARIA", nomeDaSecretaria);

        turmaB = dados.turma(escolaB, nomeDaTurma, 30);
        cursoB = jdbc.queryForObject("SELECT curso_id FROM turma WHERE id = ?", Long.class, turmaB);
        periodoB = jdbc.queryForObject("SELECT periodo_letivo_id FROM turma WHERE id = ?", Long.class, turmaB);
        disciplinaB = jdbc.queryForObject("INSERT INTO disciplina (escola_id, nome, carga_horaria) "
                + "VALUES (?, ?, 60) RETURNING id", Long.class, escolaB, nomeDaDisciplina);

        BigDecimal cem = new BigDecimal("100.00");
        cobrancaPagaB = dados.cobrancaPaga(escolaB, alunoB, cem, LocalDate.of(2026, 3, 10), Instant.now());
        dados.pagamentoOnline(escolaB, cobrancaPagaB, cem, FormaPagamento.PIX);
        cobrancaAbertaB = dados.cobranca(escolaB, alunoB, StatusCobranca.PENDENTE, LocalDate.now().plusDays(10));
        despesaB = dados.despesa(escolaB, dados.categoriaDespesa(escolaB), cem, LocalDate.now().plusDays(5),
                SituacaoDespesa.A_PAGAR, null);
        faturaB = dados.fatura(escolaB, secretariaB, "2026-01", cem, StatusCobranca.PAGA);
        dados.acaoNoLog(escolaB, secretariaB, OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, cobrancaPagaB,
                Instant.now());
    }

    private MockHttpServletResponse chamar(MockHttpServletRequestBuilder requisicao, String token) throws Exception {
        MockHttpServletResponse resposta = mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token)).andReturn().getResponse();
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return resposta;
    }

    private static MockHttpServletRequestBuilder comJson(MockHttpServletRequestBuilder requisicao, String json) {
        return requisicao.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void naoLeRegistroDeOutraEscolaPeloId() throws Exception {
        List<String> urls = List.of(
                "/alunos/" + alunoB,
                "/alunos/" + alunoB + "/financeiro",
                "/alunos/" + alunoB + "/responsaveis",
                "/matriculas/" + matriculaB,
                "/professores/" + professorB,
                "/professores/" + professorB + "/alocacoes",
                "/turmas/" + turmaB + "/alocacoes",
                "/turmas/" + turmaB + "/horario",
                "/cobrancas/" + cobrancaPagaB,
                "/cobrancas/" + cobrancaPagaB + "/comprovante",
                "/despesas/" + despesaB,
                "/usuarios/equipe/" + secretariaB,
                "/config-pagamento/faturas/" + faturaB,
                "/config-pagamento/faturas/" + faturaB + "/comprovante");

        for (String url : urls) {
            assertThat(chamar(get(url), masterDaEscolaB).getStatus()).as("dono do dado em %s", url).isEqualTo(200);
            assertThat(chamar(get(url), masterDaEscolaA).getStatus()).as("outra escola em %s", url).isEqualTo(404);
        }
    }

    @Test
    void listagensNaoTrazemNadaDeOutraEscola() throws Exception {
        List<String> urls = List.of(
                "/alunos", "/alunos/pagina",
                "/matriculas", "/matriculas/pagina",
                "/professores", "/professores/pagina",
                "/academico/secretaria/turmas", "/academico/secretaria/turmas/pagina",
                "/academico/secretaria/disciplinas",
                "/cobrancas", "/cobrancas/pagina",
                "/despesas", "/despesas/pagina",
                "/usuarios/equipe",
                "/config-pagamento/faturas", "/config-pagamento/faturas/pagina",
                "/auditoria/pagina");
        List<String> marcas = List.of(nomeDoAluno, nomeDaTurma, nomeDaDisciplina, nomeDoProfessor, nomeDaSecretaria,
                alunoB.toString(), matriculaB.toString(), professorB.toString(), secretariaB.toString(),
                cobrancaPagaB.toString(), cobrancaAbertaB.toString(), despesaB.toString(), faturaB.toString());

        for (String url : urls) {
            MockHttpServletResponse doDono = chamar(get(url), masterDaEscolaB);
            assertThat(doDono.getStatus()).as("dono do dado em %s", url).isEqualTo(200);
            assertThat(marcas).as("dono do dado ve o proprio dado em %s", url)
                    .anyMatch(doDono.getContentAsString()::contains);

            MockHttpServletResponse daOutra = chamar(get(url), masterDaEscolaA);
            assertThat(daOutra.getStatus()).as("outra escola em %s", url).isEqualTo(200);
            assertThat(marcas).as("outra escola nao ve nada em %s", url)
                    .noneMatch(daOutra.getContentAsString()::contains);
        }
    }

    @Test
    void naoAlteraNemExcluiRegistroDeOutraEscola() throws Exception {
        List<MockHttpServletRequestBuilder> tentativas = List.of(
                delete("/academico/secretaria/turmas/" + turmaB),
                delete("/academico/secretaria/cursos/" + cursoB),
                delete("/academico/secretaria/disciplinas/" + disciplinaB),
                delete("/academico/secretaria/periodos-letivos/" + periodoB),
                patch("/academico/secretaria/periodos-letivos/" + periodoB + "/encerrar"),
                comJson(put("/academico/secretaria/cursos/" + cursoB), COM_NOME),
                delete("/professores/" + professorB),
                patch("/professores/" + professorB + "/inativar"),
                post("/alunos/" + alunoB + "/primeiro-acesso"),
                patch("/usuarios/equipe/" + secretariaB + "/inativar"),
                patch("/usuarios/equipe/" + secretariaB + "/ativar"),
                comJson(put("/usuarios/equipe/" + secretariaB), "{\"nome\":\"X\",\"cpf\":\"52998224725\",\"email\":\"x@x.com\"}"),
                post("/usuarios/equipe/" + secretariaB + "/primeiro-acesso"),
                patch("/cobrancas/" + cobrancaAbertaB + "/cancelar"));

        for (MockHttpServletRequestBuilder tentativa : tentativas) {
            MockHttpServletResponse resposta = chamar(tentativa, masterDaEscolaA);
            assertThat(resposta.getStatus()).as("resposta: %s", resposta.getContentAsString()).isEqualTo(404);
        }

        assertThat(linhas("SELECT COUNT(*) FROM turma WHERE id = ?", turmaB)).isEqualTo(1);
        assertThat(linhas("SELECT COUNT(*) FROM curso WHERE id = ? AND nome <> 'Nome trocado'", cursoB)).isEqualTo(1);
        assertThat(linhas("SELECT COUNT(*) FROM disciplina WHERE id = ?", disciplinaB)).isEqualTo(1);
        assertThat(linhas("SELECT COUNT(*) FROM periodo_letivo WHERE id = ?", periodoB)).isEqualTo(1);
        assertThat(linhas("SELECT COUNT(*) FROM PROFESSOR WHERE ID_PROFESSOR = ? AND ATIVO", professorB)).isEqualTo(1);
        assertThat(linhas("SELECT COUNT(*) FROM USUARIO WHERE ID_USUARIO = ?", secretariaB)).isEqualTo(1);
        assertThat(dados.statusGravado(cobrancaAbertaB)).isEqualTo(StatusCobranca.PENDENTE);
    }

    @Test
    void naoMatriculaUsandoAlunoOuTurmaDeOutraEscola() throws Exception {
        UUID escolaA = dados.escola();
        String master = jwtService.generateAccessToken(UUID.randomUUID(), "MASTER", escolaA);
        UUID alunoA = dados.aluno(escolaA);
        long turmaA = dados.turma(escolaA, "Turma A", 30);

        MockHttpServletResponse alunoDeFora = chamar(comJson(post("/matriculas"),
                "{\"alunoId\":\"" + alunoB + "\",\"turmaId\":" + turmaA + "}"), master);
        MockHttpServletResponse turmaDeFora = chamar(comJson(post("/matriculas"),
                "{\"alunoId\":\"" + alunoA + "\",\"turmaId\":" + turmaB + "}"), master);

        assertThat(alunoDeFora.getStatus()).as("resposta: %s", alunoDeFora.getContentAsString()).isEqualTo(404);
        assertThat(turmaDeFora.getStatus()).as("resposta: %s", turmaDeFora.getContentAsString()).isEqualTo(404);
        assertThat(linhas("SELECT COUNT(*) FROM MATRICULA_TURMA WHERE ALUNO_ID = ? OR TURMA_ID = ?", alunoA, turmaB))
                .isZero();
    }

    @Test
    void alunoNaoAlcancaCobrancaDeOutroAlunoDaMesmaEscola() throws Exception {
        DadosDeTeste.AlunoComAcesso dono = dados.alunoComAcesso(escolaB, "Bruno Lima", "52998224725");
        DadosDeTeste.AlunoComAcesso colega = dados.alunoComAcesso(escolaB, "Ana Souza", "11144477735");
        String tokenDoDono = jwtService.generateAccessToken(dono.usuarioId(), "ALUNO", escolaB);
        String tokenDoColega = jwtService.generateAccessToken(colega.usuarioId(), "ALUNO", escolaB);
        BigDecimal cem = new BigDecimal("100.00");
        UUID cobranca = dados.cobrancaPaga(escolaB, dono.alunoId(), cem, LocalDate.of(2026, 3, 10), Instant.now());
        dados.pagamentoOnline(escolaB, cobranca, cem, FormaPagamento.PIX);
        UUID emAberto = dados.cobranca(escolaB, dono.alunoId(), StatusCobranca.PENDENTE, LocalDate.now().plusDays(10));

        for (String url : List.of("/portal/aluno/cobrancas/" + cobranca,
                "/portal/aluno/cobrancas/" + cobranca + "/comprovante")) {
            assertThat(chamar(get(url), tokenDoDono).getStatus()).as("dono em %s", url).isEqualTo(200);
            assertThat(chamar(get(url), tokenDoColega).getStatus()).as("colega em %s", url).isEqualTo(404);
        }
        assertThat(chamar(comJson(post("/portal/aluno/cobrancas/" + emAberto + "/pagar"), "{\"forma\":\"PIX\"}"),
                tokenDoColega).getStatus()).isEqualTo(404);
        assertThat(dados.situacaoNoProvedor(emAberto).name()).isEqualTo("NAO_EMITIDA");
    }

    private long linhas(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, Long.class, parametros);
    }
}
