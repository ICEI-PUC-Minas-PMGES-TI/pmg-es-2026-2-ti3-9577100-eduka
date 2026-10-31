package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort.Direction;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.dto.AtividadeResponse;
import br.pucminas.eduka.backend.dto.DespesaResponse;
import br.pucminas.eduka.backend.dto.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.MatriculaTurmaResponse;
import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.dto.ProfessorResponse;
import br.pucminas.eduka.backend.dto.TurmaResponse;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.service.AcademicoSecretariaService;
import br.pucminas.eduka.backend.service.AlunoService;
import br.pucminas.eduka.backend.service.DespesaService;
import br.pucminas.eduka.backend.service.MatriculaTurmaService;
import br.pucminas.eduka.backend.service.ProfessorService;
import br.pucminas.eduka.backend.service.RegistroAtividadesService;

/**
 * As listagens paginadas (#289) no Postgres: filtro, ordem, contagem e
 * recorte da pagina, so com os dados da escola do token. Alunos, professores
 * e matriculas ordenam pelo nome cifrado, em memoria; as demais, no banco.
 */
class PaginacaoBancoRealTest extends BancoRealTest {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private DespesaService despesaService;
    @Autowired
    private AcademicoSecretariaService academicoService;
    @Autowired
    private AlunoService alunoService;
    @Autowired
    private ProfessorService professorService;
    @Autowired
    private MatriculaTurmaService matriculaService;
    @Autowired
    private PaymentRoutingService paymentRoutingService;
    @Autowired
    private RegistroAtividadesService registroAtividadesService;

    @Test
    void despesasPaginamFiltramEOrdenamNoBanco() {
        UUID escola = dados.escola();
        UUID categoria = dados.categoriaDespesa(escola);
        LocalDate dia = LocalDate.of(2026, 10, 1);
        dados.despesa(escola, categoria, new BigDecimal("300.00"), dia.plusDays(2), SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, categoria, new BigDecimal("100.00"), dia, SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, categoria, new BigDecimal("200.00"), dia.plusDays(1), SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, categoria, new BigDecimal("999.00"), dia, SituacaoDespesa.CANCELADA, null);
        UUID outra = dados.escola();
        dados.despesa(outra, dados.categoriaDespesa(outra), new BigDecimal("50.00"), dia, SituacaoDespesa.A_PAGAR,
                null);
        autenticarComo("FINANCEIRO", escola);

        PaginaResponse<DespesaResponse> primeira = despesaService.listarPaginado(SituacaoDespesa.A_PAGAR, null,
                null, null, new Paginacao(0, 2), Ordenacao.Despesas.VENCIMENTO, null);
        assertThat(primeira.conteudo()).extracting(DespesaResponse::valor)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("200.00"));
        assertThat(primeira.conteudo()).allSatisfy(d -> assertThat(d.categoriaNome()).isNotBlank());
        assertThat(primeira).extracting(PaginaResponse::pagina, PaginaResponse::tamanho,
                PaginaResponse::totalElementos, PaginaResponse::totalPaginas).containsExactly(0, 2, 3L, 2);

        PaginaResponse<DespesaResponse> segunda = despesaService.listarPaginado(SituacaoDespesa.A_PAGAR, null,
                null, null, new Paginacao(1, 2), Ordenacao.Despesas.VENCIMENTO, null);
        assertThat(segunda.conteudo()).extracting(DespesaResponse::valor)
                .containsExactly(new BigDecimal("300.00"));

        assertThat(despesaService.listarPaginado(null, null, null, null, new Paginacao(0, 10),
                Ordenacao.Despesas.VALOR, Direction.DESC).conteudo())
                .extracting(DespesaResponse::valor)
                .containsExactly(new BigDecimal("999.00"), new BigDecimal("300.00"), new BigDecimal("200.00"),
                        new BigDecimal("100.00"));

        PaginaResponse<DespesaResponse> depoisDoFim = despesaService.listarPaginado(null, null, null, null,
                new Paginacao(9, 2), Ordenacao.Despesas.VENCIMENTO, null);
        assertThat(depoisDoFim.conteudo()).isEmpty();
        assertThat(depoisDoFim.totalElementos()).isEqualTo(4);
    }

    @Test
    void turmasPaginamComBuscaPeloNome() {
        UUID escola = dados.escola();
        dados.turma(escola, "1º Ano A", 30);
        dados.turma(escola, "1º Ano B", 30);
        dados.turma(escola, "2º Ano A", 30);
        UUID outra = dados.escola();
        dados.turma(outra, "1º Ano C", 30);

        PaginaResponse<TurmaResponse> pagina = academicoService.listarTurmas(escola, null, null, null, "1º ANO",
                new Paginacao(0, 10), Ordenacao.Turmas.NOME, Direction.DESC);

        assertThat(pagina.conteudo()).extracting(TurmaResponse::getNome).containsExactly("1º Ano B", "1º Ano A");
        assertThat(pagina.conteudo()).allSatisfy(t -> assertThat(t.getPeriodoLetivoNome()).isNotBlank());
        assertThat(pagina.totalElementos()).isEqualTo(2);
    }

    @Test
    void alunosOrdenamPeloNomeCifradoSemDiferenciarAcentoNemMaiuscula() {
        UUID escola = dados.escola();
        UUID alvaro = dados.aluno(escola, "Álvaro");
        dados.aluno(escola, "Bruna");
        dados.aluno(escola, "alice");
        dados.matricula(escola, alvaro, dados.turma(escola, "1A", 30), StatusMatricula.ATIVA, LocalDate.now());
        dados.aluno(dados.escola(), "Aaron");
        autenticarComo("SECRETARIA", escola);

        PaginaResponse<AlunoResponse> primeira = alunoService.listarPaginado(null, null, new Paginacao(0, 2),
                Ordenacao.Alunos.NOME, null);
        assertThat(primeira.conteudo()).extracting(AlunoResponse::nome).containsExactly("alice", "Álvaro");
        assertThat(primeira).extracting(PaginaResponse::totalElementos, PaginaResponse::totalPaginas)
                .containsExactly(3L, 2);
        assertThat(alunoService.listarPaginado(null, null, new Paginacao(1, 2), Ordenacao.Alunos.NOME, null)
                .conteudo()).extracting(AlunoResponse::nome).containsExactly("Bruna");

        assertThat(alunoService.listarPaginado("a", AlunoResponse.Situacao.SEM_TURMA, new Paginacao(0, 10),
                Ordenacao.Alunos.NOME, Direction.DESC).conteudo())
                .extracting(AlunoResponse::nome).containsExactly("Bruna", "alice");
    }

    @Test
    void professoresEMatriculasOrdenamPeloNomeEmMemoria() {
        UUID escola = dados.escola();
        dados.professor(escola, "Carla", LocalDate.of(2020, 1, 1), true);
        dados.professor(escola, "Bruno", LocalDate.of(2024, 1, 1), true);
        dados.professor(escola, "Ana", LocalDate.of(2022, 1, 1), false);
        long turma = dados.turma(escola, "1A", 30);
        dados.matricula(escola, dados.aluno(escola, "Zeca"), turma, StatusMatricula.ATIVA, LocalDate.of(2026, 2, 1));
        dados.matricula(escola, dados.aluno(escola, "Ívis"), turma, StatusMatricula.ATIVA, LocalDate.of(2026, 3, 1));
        autenticarComo("SECRETARIA", escola);

        assertThat(professorService.listarPaginado(null, true, new Paginacao(0, 10), Ordenacao.Professores.NOME,
                null).conteudo()).extracting(ProfessorResponse::nome).containsExactly("Bruno", "Carla");
        assertThat(professorService.listarPaginado(null, null, new Paginacao(0, 10),
                Ordenacao.Professores.DATA_ADMISSAO, Direction.DESC).conteudo())
                .extracting(ProfessorResponse::nome).containsExactly("Bruno", "Ana", "Carla");

        assertThat(matriculaService.listarPaginado(turma, null, null, new Paginacao(0, 10),
                Ordenacao.Matriculas.ALUNO, null).conteudo())
                .extracting(MatriculaTurmaResponse::alunoNome).containsExactly("Ívis", "Zeca");
        assertThat(matriculaService.listarPaginado(turma, null, null, new Paginacao(0, 1),
                Ordenacao.Matriculas.DATA_MATRICULA, Direction.DESC))
                .extracting(p -> p.conteudo().get(0).alunoNome(), PaginaResponse::totalPaginas)
                .containsExactly("Ívis", 2);
    }

    @Test
    void faturasDaEscolaVemDaCompetenciaMaisRecenteEFiltramPorSituacao() {
        UUID escola = dados.escola();
        UUID superAdmin = dados.usuario(escola, "MASTER", "Admin");
        dados.fatura(escola, superAdmin, "2026-08", new BigDecimal("199.90"), StatusCobranca.PAGA);
        dados.fatura(escola, superAdmin, "2026-10", new BigDecimal("199.90"), StatusCobranca.PENDENTE);
        dados.fatura(escola, superAdmin, "2026-09", new BigDecimal("199.90"), StatusCobranca.PAGA);
        UUID outra = dados.escola();
        dados.fatura(outra, dados.usuario(outra, "MASTER", "Outro"), "2026-11", new BigDecimal("1.00"),
                StatusCobranca.PENDENTE);
        autenticarComo("MASTER", escola);

        assertThat(paymentRoutingService.listarMinhasFaturas(null, new Paginacao(0, 10),
                Ordenacao.Faturas.COMPETENCIA, null).conteudo())
                .extracting(FaturaPlataformaResponse::competencia)
                .containsExactly("2026-10", "2026-09", "2026-08");
        assertThat(paymentRoutingService.listarFaturas(escola, StatusCobranca.PAGA, new Paginacao(0, 1),
                Ordenacao.Faturas.COMPETENCIA, Direction.ASC))
                .extracting(p -> p.conteudo().get(0).competencia(), PaginaResponse::totalElementos)
                .containsExactly("2026-08", 2L);
    }

    @Test
    void atividadesDaEscolaComAutorEFiltros() {
        UUID escola = dados.escola();
        UUID maria = dados.usuario(escola, "FINANCEIRO", "Maria");
        UUID joao = dados.usuario(escola, "SECRETARIA", "João");
        Instant ontem = LocalDate.of(2026, 10, 30).atTime(23, 30).atZone(FUSO_ESCOLA).toInstant();
        Instant hoje = LocalDate.of(2026, 10, 31).atTime(0, 30).atZone(FUSO_ESCOLA).toInstant();
        UUID registro = UUID.randomUUID();
        dados.acaoNoLog(escola, maria, OperacaoAuditoria.CRIAR, EntidadeAuditada.DESPESA, registro, ontem);
        dados.acaoNoLog(escola, maria, OperacaoAuditoria.PAGAR, EntidadeAuditada.DESPESA, registro, hoje);
        dados.acaoNoLog(escola, joao, OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, UUID.randomUUID(), hoje);
        UUID outra = dados.escola();
        dados.acaoNoLog(outra, dados.usuario(outra, "MASTER", "Fora"), OperacaoAuditoria.CRIAR,
                EntidadeAuditada.DESPESA, UUID.randomUUID(), hoje);
        autenticarComo("MASTER", escola);

        PaginaResponse<AtividadeResponse> todas = registroAtividadesService.listar(null, null, null, null, null,
                new Paginacao(0, 10), null);
        assertThat(todas.totalElementos()).isEqualTo(3);
        assertThat(todas.conteudo().get(2))
                .extracting(AtividadeResponse::usuarioNome, AtividadeResponse::usuarioPerfil,
                        AtividadeResponse::operacao)
                .containsExactly("Maria", PerfilUsuario.FINANCEIRO, OperacaoAuditoria.CRIAR);

        // O dia e o da escola: 23h30 de 30/10 em Sao Paulo ja e 31/10 em UTC, e fica de fora.
        assertThat(registroAtividadesService.listar(maria, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31),
                EntidadeAuditada.DESPESA, null, new Paginacao(0, 10), null).conteudo())
                .extracting(AtividadeResponse::operacao, AtividadeResponse::usuarioNome)
                .containsExactly(tuple(OperacaoAuditoria.PAGAR, "Maria"));

        assertThatThrownBy(() -> registroAtividadesService.listar(null, LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 10, 30), null, null, new Paginacao(0, 10), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
