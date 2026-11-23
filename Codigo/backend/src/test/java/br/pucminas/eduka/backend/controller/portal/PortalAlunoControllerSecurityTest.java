package br.pucminas.eduka.backend.controller.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

import br.pucminas.eduka.backend.domain.academico.Curso;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.OrigemPagamento;
import br.pucminas.eduka.backend.domain.financeiro.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.NotaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ResponsavelRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.privacidade.PoliticaRetencao;
import br.pucminas.eduka.backend.service.acesso.AcessoService;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;
import br.pucminas.eduka.backend.service.financeiro.AvisoCobrancaService;
import br.pucminas.eduka.backend.service.financeiro.CobrancaService;
import br.pucminas.eduka.backend.service.pessoas.AlunoService;
import br.pucminas.eduka.backend.service.pessoas.ResponsavelService;
import br.pucminas.eduka.backend.service.portal.PortalAlunoService;

/**
 * Contrato de seguranca de {@code /portal/aluno}, ponta a ponta: filter
 * chain, tokens, {@code @PreAuthorize} e servico reais; so os repositorios
 * e o acesso (que a leitura nao usa) sao mock.
 */
@WebMvcTest(PortalAlunoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        AlunoService.class, PoliticaRetencao.class, ResponsavelService.class, PortalAlunoService.class,
        CobrancaService.class })
class PortalAlunoControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String URL = "/portal/aluno/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AlunoRepository alunoRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private PagamentoCobrancaRepository pagamentoRepository;
    @MockitoBean
    private PaymentRoutingService paymentRoutingService;
    @MockitoBean
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private PessoaPapelRepository pessoaPapelRepository;
    @MockitoBean
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private AvisoCobrancaService avisoCobrancaService;
    @MockitoBean
    private ResponsavelRepository responsavelRepository;
    @MockitoBean
    private CobrancaRepository cobrancaRepository;
    @MockitoBean
    private AcessoService acessoService;
    @MockitoBean
    private MatriculaTurmaRepository matriculaRepository;
    @MockitoBean
    private AlocacaoProfessorRepository alocacaoRepository;
    @MockitoBean
    private AvaliacaoRepository avaliacaoRepository;
    @MockitoBean
    private NotaRepository notaRepository;
    @MockitoBean
    private FrequenciaRepository frequenciaRepository;

    private String accessToken(UUID usuarioId, String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(usuarioId, perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soAlunoAcessa() throws Exception {
        for (String url : List.of(URL, "/portal/aluno/turmas", "/portal/aluno/boletim", "/portal/aluno/avaliacoes",
                "/portal/aluno/financeiro")) {
            for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR")) {
                mockMvc.perform(com(get(url), accessToken(UUID.randomUUID(), perfil, ESCOLA_A)))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(com(get(url), accessToken(UUID.randomUUID(), "SUPER_ADMIN", null)))
                    .andExpect(status().isForbidden());
        }
        verify(alunoRepository, never()).findByUsuarioIdAndEscolaId(any(), any());
    }

    @Test
    void devolveOsDadosDoAlunoDoToken() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana")
                .cpf("52998224725").email("ana@escola.com").dataNascimento("2010-03-15")
                .cidade("Belo Horizonte").ativo(true).build();
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoa.getId())
                .usuarioId(usuarioId).matricula("2026-001").build();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(aluno));
        when(pessoaRepository.findByIdAndEscolaId(pessoa.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoa));

        mockMvc.perform(com(get(URL), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aluno.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Ana"))
                .andExpect(jsonPath("$.matricula").value("2026-001"))
                .andExpect(jsonPath("$.dataNascimento").value("2010-03-15"))
                .andExpect(jsonPath("$.endereco.cidade").value("Belo Horizonte"))
                .andExpect(jsonPath("$.responsaveis.length()").value(0));

        verify(alunoRepository, never()).findAll();
        verify(alunoRepository, never()).findByIdAndEscolaId(any(), any());
    }

    @Test
    void loginSemCadastroDeAlunoResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.empty());

        mockMvc.perform(com(get(URL), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aluno nao encontrado"));
    }

    @Test
    void alunoSemMatriculaRecebeBoletimEAgendaVazios() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(UUID.randomUUID())
                .usuarioId(usuarioId).matricula("2026-001").build();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(aluno));

        for (String url : List.of("/portal/aluno/turmas", "/portal/aluno/boletim", "/portal/aluno/avaliacoes")) {
            mockMvc.perform(com(get(url), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Test
    void boletimTrazAFrequenciaComNuloParaDisciplinaSemAulas() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(UUID.randomUUID())
                .usuarioId(usuarioId).matricula("2026-001").build();
        Disciplina matematica = Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build();
        Disciplina fisica = Disciplina.builder().id(11L).nome("Fisica").cargaHoraria(60).build();
        Turma turma = Turma.builder().id(1L).nome("1A").turno(Turno.MATUTINO).vagas(30)
                .periodoLetivo(PeriodoLetivo.builder().id(5L).nome("2026").build())
                .curso(Curso.builder().id(7L).nome("Ensino Medio").build())
                .disciplinas(new java.util.HashSet<>(List.of(matematica, fisica))).build();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(aluno));
        when(matriculaRepository.findAllByEscolaIdAndAlunoId(ESCOLA_A, aluno.getId())).thenReturn(List.of(
                MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(aluno).turma(turma)
                        .status(StatusMatricula.ATIVA).dataMatricula(LocalDate.of(2026, 2, 1)).build()));
        when(frequenciaRepository.resumirPorDisciplina(ESCOLA_A, aluno.getId(), 1L)).thenReturn(List.of(
                new FrequenciaRepository.FrequenciaDaDisciplina() {
                    public Long getDisciplinaId() {
                        return 10L;
                    }

                    public long getAulas() {
                        return 20;
                    }

                    public long getFaltas() {
                        return 6;
                    }
                }));

        mockMvc.perform(com(get("/portal/aluno/boletim"), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].limiteFaltasPercentual").value(25))
                .andExpect(jsonPath("$[0].frequencia.totalAulas").value(20))
                .andExpect(jsonPath("$[0].frequencia.faltas").value(6))
                .andExpect(jsonPath("$[0].frequencia.percentualFrequencia").value(70.0))
                .andExpect(jsonPath("$[0].frequencia.alertaFaltas").value(true))
                .andExpect(jsonPath("$[0].disciplinas[0].disciplinaNome").value("Fisica"))
                .andExpect(jsonPath("$[0].disciplinas[0].frequencia").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].disciplinas[1].frequencia.totalAulas").value(20))
                .andExpect(jsonPath("$[0].disciplinas[1].frequencia.alertaFaltas").value(true));
    }

    @Test
    void financeiroTrazAsCobrancasDoAlunoDoTokenComResumo() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoa.getId())
                .usuarioId(usuarioId).matricula("2026-001").build();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(aluno));
        when(pessoaRepository.findByIdAndEscolaId(pessoa.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoa));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoa));
        when(cobrancaRepository.findAllByEscolaIdAndAlunoId(ESCOLA_A, aluno.getId())).thenReturn(List.of(
                cobranca(aluno, "Janeiro", "500.00", LocalDate.of(2020, 1, 10), StatusCobranca.PAGA),
                // Pendente vencida que a rotina ainda nao marcou: ja conta como em atraso.
                cobranca(aluno, "Fevereiro", "500.00", LocalDate.of(2020, 2, 10), StatusCobranca.PENDENTE),
                cobranca(aluno, "Taxa", "80.00", LocalDate.of(2020, 3, 1), StatusCobranca.CANCELADA),
                cobranca(aluno, "Dezembro", "500.00", LocalDate.of(2099, 12, 10), StatusCobranca.PENDENTE)));

        mockMvc.perform(com(get("/portal/aluno/financeiro"), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alunoId").value(aluno.getId().toString()))
                .andExpect(jsonPath("$.alunoNome").value("Ana"))
                .andExpect(jsonPath("$.resumo.valorEmAberto").value(1000.00))
                .andExpect(jsonPath("$.resumo.valorEmAtraso").value(500.00))
                .andExpect(jsonPath("$.resumo.valorPago").value(500.00))
                .andExpect(jsonPath("$.resumo.quantidadePendente").value(1))
                .andExpect(jsonPath("$.resumo.quantidadeEmAtraso").value(1))
                .andExpect(jsonPath("$.resumo.quantidadePaga").value(1))
                .andExpect(jsonPath("$.resumo.quantidadeCancelada").value(1))
                .andExpect(jsonPath("$.cobrancas.length()").value(4))
                .andExpect(jsonPath("$.cobrancas[0].descricao").value("Dezembro"))
                .andExpect(jsonPath("$.cobrancas[2].status").value("EM_ATRASO"))
                .andExpect(jsonPath("$.cobrancas[3].descricao").value("Janeiro"));

        verify(cobrancaRepository, never()).findAllByEscolaId(any());
    }

    @Test
    void financeiroTrazOPagamentoEOComprovantePelaRotaDoPortal() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoa.getId())
                .usuarioId(usuarioId).matricula("2026-001").build();
        Cobranca online = cobranca(aluno, "Fevereiro", "500.00", LocalDate.of(2026, 2, 10), StatusCobranca.PAGA);
        Cobranca manual = cobranca(aluno, "Janeiro", "450.00", LocalDate.of(2026, 1, 10), StatusCobranca.PAGA);
        // 01:30 UTC do dia 9 ainda e dia 8 na escola.
        manual.setPagaEm(Instant.parse("2026-01-09T01:30:00Z"));
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(aluno));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoa));
        when(cobrancaRepository.findAllByEscolaIdAndAlunoId(ESCOLA_A, aluno.getId()))
                .thenReturn(List.of(manual, online));
        when(pagamentoRepository.findAllByEscolaIdAndCobrancaIdIn(eq(ESCOLA_A), any())).thenReturn(List.of(
                PagamentoCobranca.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).cobrancaId(online.getId())
                        .valorPago(new BigDecimal("500.00")).dataPagamento(LocalDate.of(2026, 2, 8))
                        .formaPagamento(FormaPagamento.PIX).origem(OrigemPagamento.ONLINE)
                        .idTransacaoExterna("pay_123").urlReciboProvedor("https://www.asaas.com/i/123").build()));

        mockMvc.perform(com(get("/portal/aluno/financeiro"), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobrancas[0].descricao").value("Fevereiro"))
                .andExpect(jsonPath("$.cobrancas[0].pagamento.origem").value("ONLINE"))
                .andExpect(jsonPath("$.cobrancas[0].pagamento.forma").value("PIX"))
                .andExpect(jsonPath("$.cobrancas[0].pagamento.valorPago").value(500.00))
                .andExpect(jsonPath("$.cobrancas[0].pagamento.comprovanteUrl")
                        .value("/portal/aluno/cobrancas/" + online.getId() + "/comprovante"))
                // Nada do provedor sai no historico.
                .andExpect(jsonPath("$.cobrancas[0].pagamento.urlReciboProvedor").doesNotExist())
                .andExpect(jsonPath("$.cobrancas[0].pagamento.idTransacaoExterna").doesNotExist())
                .andExpect(jsonPath("$.cobrancas[1].pagamento.origem").value("MANUAL"))
                .andExpect(jsonPath("$.cobrancas[1].pagamento.valorPago").value(450.00))
                .andExpect(jsonPath("$.cobrancas[1].pagamento.dataPagamento").value("2026-01-08"))
                .andExpect(jsonPath("$.cobrancas[1].pagamento.forma").doesNotExist())
                .andExpect(jsonPath("$.cobrancas[1].pagamento.comprovanteUrl").doesNotExist());
    }

    @Test
    void financeiroDeLoginSemCadastroDeAlunoResponde404() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.empty());

        mockMvc.perform(com(get("/portal/aluno/financeiro"), accessToken(usuarioId, "ALUNO", ESCOLA_A)))
                .andExpect(status().isNotFound());
        verify(cobrancaRepository, never()).findAllByEscolaIdAndAlunoId(any(), any());
    }

    private static Cobranca cobranca(Aluno aluno, String descricao, String valor, LocalDate vencimento,
            StatusCobranca status) {
        return Cobranca.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).aluno(aluno).descricao(descricao)
                .valor(new BigDecimal(valor)).dataVencimento(vencimento).status(status).build();
    }
}
