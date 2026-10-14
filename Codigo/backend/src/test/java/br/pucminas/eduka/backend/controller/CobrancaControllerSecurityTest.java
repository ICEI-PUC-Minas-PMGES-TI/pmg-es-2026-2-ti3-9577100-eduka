package br.pucminas.eduka.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.LogAuditoria;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.LogAuditoriaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.AuditoriaService;
import br.pucminas.eduka.backend.service.CobrancaService;

/**
 * Contrato das cobrancas, ponta a ponta: filter chain, tokens,
 * {@code @PreAuthorize}, validacao e servico reais; so os repositorios sao
 * mock.
 */
@WebMvcTest(CobrancaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        CobrancaService.class, AuditoriaService.class })
class CobrancaControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final LocalDate NO_PRAZO = LocalDate.of(2099, 1, 10);
    private static final LocalDate VENCIDA = LocalDate.of(2020, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CobrancaRepository cobrancaRepository;
    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private MatriculaTurmaRepository matriculaRepository;
    @MockitoBean
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private LogAuditoriaRepository logAuditoriaRepository;

    private Pessoa pessoaAna;
    private Pessoa maeDaAna;
    private Aluno ana;
    private Aluno bruno;
    private MatriculaTurma matriculaDaAna;
    private MatriculaTurma matriculaDoBruno;

    @BeforeEach
    void dados() {
        pessoaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        maeDaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Marta").ativo(true).build();
        ana = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoaAna.getId())
                .matricula("2026001").build();
        bruno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(UUID.randomUUID())
                .matricula("2026002").build();
        Turma turma = Turma.builder().id(1L).nome("6A").build();
        matriculaDaAna = MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(ana).turma(turma)
                .status(StatusMatricula.ATIVA).build();
        matriculaDoBruno = MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(bruno)
                .turma(turma).status(StatusMatricula.ATIVA).build();

        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(matriculaRepository.findByIdAndEscolaId(matriculaDaAna.getId(), ESCOLA_A))
                .thenReturn(Optional.of(matriculaDaAna));
        when(matriculaRepository.findByIdAndEscolaId(matriculaDoBruno.getId(), ESCOLA_A))
                .thenReturn(Optional.of(matriculaDoBruno));
        when(pessoaRepository.findByIdAndEscolaId(maeDaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(maeDaAna));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A)))
                .thenReturn(List.of(pessoaAna, maeDaAna));
        when(alunoResponsavelRepository.existsById(new AlunoResponsavelId(ana.getId(), maeDaAna.getId())))
                .thenReturn(true);
        when(cobrancaRepository.saveAndFlush(any(Cobranca.class))).thenAnswer(inv -> {
            Cobranca cobranca = inv.getArgument(0);
            if (cobranca.getId() == null) {
                cobranca.setId(UUID.randomUUID());
            }
            return cobranca;
        });
    }

    private String accessToken(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token,
            String corpo) {
        return com(request, token).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static final String DADOS = "\"descricao\":\"Mensalidade de janeiro\",\"valor\":850.50,"
            + "\"dataVencimento\":\"2099-01-10\"";

    private String cobrarAna(String extras) {
        return "{\"alunoId\":\"" + ana.getId() + "\"," + DADOS + extras + "}";
    }

    private Cobranca cobrancaDaAna(StatusCobranca status, LocalDate vencimento) {
        Cobranca cobranca = Cobranca.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(ana)
                .descricao("Mensalidade").valor(new BigDecimal("850.50")).dataVencimento(vencimento).status(status)
                .build();
        when(cobrancaRepository.findByIdAndEscolaId(cobranca.getId(), ESCOLA_A)).thenReturn(Optional.of(cobranca));
        return cobranca;
    }

    // ---------- Seguranca ----------

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/cobrancas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soGestaoEFinanceiroEntramNoModulo() throws Exception {
        for (String perfil : List.of("SECRETARIA", "PROFESSOR", "ALUNO")) {
            String token = accessToken(perfil, ESCOLA_A);
            mockMvc.perform(com(get("/cobrancas"), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(post("/cobrancas"), token, cobrarAna("")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(patch("/cobrancas/" + UUID.randomUUID() + "/cancelar"), token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(json(post("/cobrancas"), accessToken("SUPER_ADMIN", null), cobrarAna("")))
                .andExpect(status().isForbidden());
        verify(cobrancaRepository, never()).saveAndFlush(any());

        mockMvc.perform(com(get("/cobrancas"), accessToken("MASTER", ESCOLA_A)))
                .andExpect(status().isOk());
    }

    // ---------- Criar ----------

    @Test
    void financeiroLancaCobrancaPendenteNaEscolaDoToken() throws Exception {
        mockMvc.perform(json(post("/cobrancas"), accessToken("FINANCEIRO", ESCOLA_A),
                cobrarAna(",\"matriculaId\":\"" + matriculaDaAna.getId() + "\",\"responsavelId\":\""
                        + maeDaAna.getId() + "\"")))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.alunoNome").value("Ana"))
                .andExpect(jsonPath("$.alunoNumeroMatricula").value("2026001"))
                .andExpect(jsonPath("$.matriculaId").value(matriculaDaAna.getId().toString()))
                .andExpect(jsonPath("$.turmaNome").value("6A"))
                .andExpect(jsonPath("$.responsavelNome").value("Marta"))
                .andExpect(jsonPath("$.descricao").value("Mensalidade de janeiro"))
                .andExpect(jsonPath("$.valor").value(850.50))
                .andExpect(jsonPath("$.dataVencimento").value("2099-01-10"))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        ArgumentCaptor<Cobranca> salva = ArgumentCaptor.forClass(Cobranca.class);
        verify(cobrancaRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
    }

    @Test
    void matriculaEResponsavelSaoOpcionais() throws Exception {
        mockMvc.perform(json(post("/cobrancas"), accessToken("MASTER", ESCOLA_A), cobrarAna("")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matriculaId").doesNotExist())
                .andExpect(jsonPath("$.responsavelId").doesNotExist());
    }

    @Test
    void dadosInvalidosRespondem400() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        String aluno = "\"alunoId\":\"" + ana.getId() + "\"";
        for (String corpo : List.of(
                "{" + DADOS + "}",
                "{" + aluno + ",\"descricao\":\" \",\"valor\":10,\"dataVencimento\":\"2099-01-10\"}",
                "{" + aluno + ",\"descricao\":\"Taxa\",\"valor\":0,\"dataVencimento\":\"2099-01-10\"}",
                "{" + aluno + ",\"descricao\":\"Taxa\",\"valor\":10.123,\"dataVencimento\":\"2099-01-10\"}",
                "{" + aluno + ",\"descricao\":\"Taxa\",\"valor\":10}")) {
            mockMvc.perform(json(post("/cobrancas"), token, corpo))
                    .andExpect(status().isBadRequest());
        }
        verify(cobrancaRepository, never()).saveAndFlush(any());
    }

    @Test
    void alunoMatriculaOuResponsavelDeOutraEscolaRespondem404() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        mockMvc.perform(json(post("/cobrancas"), token, "{\"alunoId\":\"" + UUID.randomUUID() + "\"," + DADOS + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aluno nao encontrado"));
        mockMvc.perform(json(post("/cobrancas"), token, cobrarAna(",\"matriculaId\":\"" + UUID.randomUUID() + "\"")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Matricula nao encontrada"));
        mockMvc.perform(json(post("/cobrancas"), token,
                cobrarAna(",\"responsavelId\":\"" + UUID.randomUUID() + "\"")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Responsavel nao encontrado"));
        verify(cobrancaRepository, never()).saveAndFlush(any());
    }

    @Test
    void matriculaOuResponsavelDeOutroAlunoRespondem400() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        mockMvc.perform(json(post("/cobrancas"), token,
                cobrarAna(",\"matriculaId\":\"" + matriculaDoBruno.getId() + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Matricula nao pertence ao aluno"));

        when(alunoResponsavelRepository.existsById(any())).thenReturn(false);
        mockMvc.perform(json(post("/cobrancas"), token, cobrarAna(",\"responsavelId\":\"" + maeDaAna.getId() + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Responsavel nao esta vinculado ao aluno"));
        verify(cobrancaRepository, never()).saveAndFlush(any());
    }

    // ---------- Editar ----------

    @Test
    void editaCobrancaEmAberto() throws Exception {
        Cobranca cobranca = cobrancaDaAna(StatusCobranca.PENDENTE, VENCIDA);
        cobranca.setMatricula(matriculaDaAna);

        mockMvc.perform(json(put("/cobrancas/" + cobranca.getId()), accessToken("FINANCEIRO", ESCOLA_A),
                "{\"descricao\":\"  Mensalidade renegociada \",\"valor\":900,\"dataVencimento\":\"2099-02-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Mensalidade renegociada"))
                .andExpect(jsonPath("$.valor").value(900))
                .andExpect(jsonPath("$.dataVencimento").value("2099-02-10"))
                .andExpect(jsonPath("$.matriculaId").doesNotExist())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.atualizadoEm").isNotEmpty());
    }

    @Test
    void adiarOVencimentoTiraACobrancaDoAtraso() throws Exception {
        Cobranca cobranca = cobrancaDaAna(StatusCobranca.EM_ATRASO, VENCIDA);

        mockMvc.perform(json(put("/cobrancas/" + cobranca.getId()), accessToken("FINANCEIRO", ESCOLA_A),
                "{" + DADOS + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"));
        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.PENDENTE);
    }

    @Test
    void vencimentoNoPassadoPoeACobrancaEmAtraso() throws Exception {
        Cobranca cobranca = cobrancaDaAna(StatusCobranca.PENDENTE, NO_PRAZO);

        mockMvc.perform(json(put("/cobrancas/" + cobranca.getId()), accessToken("FINANCEIRO", ESCOLA_A),
                "{\"descricao\":\"Mensalidade\",\"valor\":850.50,\"dataVencimento\":\"2020-01-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATRASO"));
        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.EM_ATRASO);
    }

    @Test
    void cobrancaPagaOuCanceladaNaoEEditadaNemCancelada() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        for (StatusCobranca status : List.of(StatusCobranca.PAGA, StatusCobranca.CANCELADA)) {
            Cobranca cobranca = cobrancaDaAna(status, NO_PRAZO);
            mockMvc.perform(json(put("/cobrancas/" + cobranca.getId()), token, "{" + DADOS + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Cobranca " + status + " nao pode ser editada"));
            mockMvc.perform(com(patch("/cobrancas/" + cobranca.getId() + "/cancelar"), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Cobranca " + status + " nao pode ser cancelada"));
        }
        verify(cobrancaRepository, never()).saveAndFlush(any());
    }

    // ---------- Cancelar ----------

    @Test
    void cancelaCobrancaEmAberto() throws Exception {
        for (StatusCobranca status : List.of(StatusCobranca.PENDENTE, StatusCobranca.EM_ATRASO)) {
            Cobranca cobranca = cobrancaDaAna(status, VENCIDA);

            mockMvc.perform(com(patch("/cobrancas/" + cobranca.getId() + "/cancelar"),
                    accessToken("MASTER", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELADA"))
                    .andExpect(jsonPath("$.canceladaEm").isNotEmpty());
            assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.CANCELADA);
        }
    }

    @Test
    void cobrancaDeOutraEscolaResponde404() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        String id = UUID.randomUUID().toString();
        mockMvc.perform(com(get("/cobrancas/" + id), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cobranca nao encontrada"));
        mockMvc.perform(json(put("/cobrancas/" + id), token, "{" + DADOS + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(com(patch("/cobrancas/" + id + "/cancelar"), token))
                .andExpect(status().isNotFound());
    }

    // ---------- Auditoria ----------

    /** Unico log gravado ate aqui. */
    private LogAuditoria logGravado() {
        ArgumentCaptor<LogAuditoria> log = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(logAuditoriaRepository).save(log.capture());
        return log.getValue();
    }

    @Test
    void criarRegistraNoLogQuemLancouACobranca() throws Exception {
        UUID usuario = UUID.randomUUID();
        String token = jwtService.generateAccessToken(usuario, "FINANCEIRO", ESCOLA_A);

        mockMvc.perform(json(post("/cobrancas"), token, cobrarAna("")))
                .andExpect(status().isCreated());

        ArgumentCaptor<Cobranca> salva = ArgumentCaptor.forClass(Cobranca.class);
        verify(cobrancaRepository).saveAndFlush(salva.capture());
        LogAuditoria log = logGravado();
        assertThat(log.getUsuarioId()).isEqualTo(usuario);
        assertThat(log.getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(log.getOperacao()).isEqualTo(OperacaoAuditoria.CRIAR);
        assertThat(log.getEntidade()).isEqualTo(EntidadeAuditada.COBRANCA);
        assertThat(log.getRegistroId()).isEqualTo(salva.getValue().getId());
        assertThat(log.getDataHora()).isNotNull();
    }

    @Test
    void editarRegistraNoLogQuemEditouACobranca() throws Exception {
        UUID usuario = UUID.randomUUID();
        Cobranca cobranca = cobrancaDaAna(StatusCobranca.PENDENTE, NO_PRAZO);

        mockMvc.perform(json(put("/cobrancas/" + cobranca.getId()),
                jwtService.generateAccessToken(usuario, "FINANCEIRO", ESCOLA_A), "{" + DADOS + "}"))
                .andExpect(status().isOk());

        LogAuditoria log = logGravado();
        assertThat(log.getUsuarioId()).isEqualTo(usuario);
        assertThat(log.getOperacao()).isEqualTo(OperacaoAuditoria.EDITAR);
        assertThat(log.getRegistroId()).isEqualTo(cobranca.getId());
    }

    @Test
    void cancelarRegistraNoLogQuemCancelouACobranca() throws Exception {
        UUID usuario = UUID.randomUUID();
        Cobranca cobranca = cobrancaDaAna(StatusCobranca.PENDENTE, NO_PRAZO);

        mockMvc.perform(com(patch("/cobrancas/" + cobranca.getId() + "/cancelar"),
                jwtService.generateAccessToken(usuario, "MASTER", ESCOLA_A)))
                .andExpect(status().isOk());

        LogAuditoria log = logGravado();
        assertThat(log.getUsuarioId()).isEqualTo(usuario);
        assertThat(log.getOperacao()).isEqualTo(OperacaoAuditoria.CANCELAR);
        assertThat(log.getRegistroId()).isEqualTo(cobranca.getId());
    }

    @Test
    void operacaoRecusadaOuSoDeLeituraNaoGeraLog() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        Cobranca paga = cobrancaDaAna(StatusCobranca.PAGA, NO_PRAZO);

        mockMvc.perform(json(put("/cobrancas/" + paga.getId()), token, "{" + DADOS + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(patch("/cobrancas/" + paga.getId() + "/cancelar"), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/cobrancas"), token, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(get("/cobrancas/" + paga.getId()), token))
                .andExpect(status().isOk());

        verify(logAuditoriaRepository, never()).save(any());
    }

    // ---------- Consulta ----------

    @Test
    void buscaMostraPendenteVencidaComoEmAtraso() throws Exception {
        Cobranca cobranca = cobrancaDaAna(StatusCobranca.PENDENTE, VENCIDA);

        mockMvc.perform(com(get("/cobrancas/" + cobranca.getId()), accessToken("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATRASO"))
                .andExpect(jsonPath("$.alunoNome").value("Ana"));
    }

    @Test
    void listaFiltrandoPorSituacaoEVencimento() throws Exception {
        Cobranca noPrazo = cobrancaDaAna(StatusCobranca.PENDENTE, NO_PRAZO);
        Cobranca vencida = cobrancaDaAna(StatusCobranca.PENDENTE, VENCIDA);
        Cobranca marcadaPelaRotina = cobrancaDaAna(StatusCobranca.EM_ATRASO, VENCIDA.minusDays(1));
        Cobranca paga = cobrancaDaAna(StatusCobranca.PAGA, VENCIDA);
        when(cobrancaRepository.findAllByEscolaId(ESCOLA_A))
                .thenReturn(List.of(noPrazo, vencida, marcadaPelaRotina, paga));
        String token = accessToken("FINANCEIRO", ESCOLA_A);

        mockMvc.perform(com(get("/cobrancas"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].id").value(noPrazo.getId().toString()));

        // Em atraso: a que a rotina ja gravou e a pendente vencida que ela ainda nao pegou.
        mockMvc.perform(com(get("/cobrancas").param("status", "EM_ATRASO"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(marcadaPelaRotina.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(vencida.getId().toString()));

        mockMvc.perform(com(get("/cobrancas").param("vencimentoDe", "2099-01-10").param("vencimentoAte",
                "2099-01-10"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(noPrazo.getId().toString()));
    }

    @Test
    void listaPorAlunoUsaAEscolaDoToken() throws Exception {
        Cobranca cobranca = cobrancaDaAna(StatusCobranca.PENDENTE, NO_PRAZO);
        when(cobrancaRepository.findAllByEscolaIdAndAlunoId(ESCOLA_A, ana.getId())).thenReturn(List.of(cobranca));

        mockMvc.perform(com(get("/cobrancas").param("alunoId", ana.getId().toString()),
                accessToken("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(cobrancaRepository, never()).findAllByEscolaId(any());
    }

    @Test
    void filtroInvalidoResponde400() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        mockMvc.perform(com(get("/cobrancas").param("status", "XPTO"), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(get("/cobrancas").param("vencimentoDe", "10/01/2099"), token))
                .andExpect(status().isBadRequest());
    }
}
