package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort.Direction;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.financeiro.TipoCobranca;
import br.pucminas.eduka.backend.dto.academico.MatriculaTurmaResponse;
import br.pucminas.eduka.backend.dto.academico.ResumoMatriculasResponse;
import br.pucminas.eduka.backend.dto.academico.TurmaResponse;
import br.pucminas.eduka.backend.dto.auditoria.AtividadeResponse;
import br.pucminas.eduka.backend.dto.comum.Ordenacao;
import br.pucminas.eduka.backend.dto.comum.PaginaComResumoResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.dto.escola.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.financeiro.DespesaResponse;
import br.pucminas.eduka.backend.dto.financeiro.FiltroFinanceiroAlunos;
import br.pucminas.eduka.backend.dto.financeiro.FinanceiroDoAlunoResponse;
import br.pucminas.eduka.backend.dto.financeiro.ResumoDespesasResponse;
import br.pucminas.eduka.backend.dto.pessoas.AlunoResponse;
import br.pucminas.eduka.backend.dto.pessoas.ProfessorResponse;
import br.pucminas.eduka.backend.dto.pessoas.ResumoAlunosResponse;
import br.pucminas.eduka.backend.dto.pessoas.ResumoProfessoresResponse;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.service.academico.AcademicoSecretariaService;
import br.pucminas.eduka.backend.service.academico.MatriculaTurmaService;
import br.pucminas.eduka.backend.service.auditoria.RegistroAtividadesService;
import br.pucminas.eduka.backend.service.financeiro.DespesaService;
import br.pucminas.eduka.backend.service.pessoas.AlunoService;
import br.pucminas.eduka.backend.service.pessoas.ProfessorService;

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

        var primeira = despesaService.listarPaginado(List.of(SituacaoDespesa.A_PAGAR), null,
                null, null, new Paginacao(0, 2), Ordenacao.Despesas.VENCIMENTO, null);
        assertThat(primeira.conteudo()).extracting(DespesaResponse::valor)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("200.00"));
        assertThat(primeira.conteudo()).allSatisfy(d -> assertThat(d.categoriaNome()).isNotBlank());
        assertThat(primeira).extracting(PaginaComResumoResponse::pagina, PaginaComResumoResponse::tamanho,
                PaginaComResumoResponse::totalElementos, PaginaComResumoResponse::totalPaginas)
                .containsExactly(0, 2, 3L, 2);

        var segunda = despesaService.listarPaginado(List.of(SituacaoDespesa.A_PAGAR), null,
                null, null, new Paginacao(1, 2), Ordenacao.Despesas.VENCIMENTO, null);
        assertThat(segunda.conteudo()).extracting(DespesaResponse::valor)
                .containsExactly(new BigDecimal("300.00"));

        assertThat(despesaService.listarPaginado(null, null, null, null, new Paginacao(0, 10),
                Ordenacao.Despesas.VALOR, Direction.DESC).conteudo())
                .extracting(DespesaResponse::valor)
                .containsExactly(new BigDecimal("999.00"), new BigDecimal("300.00"), new BigDecimal("200.00"),
                        new BigDecimal("100.00"));

        var depoisDoFim = despesaService.listarPaginado(null, null, null, null,
                new Paginacao(9, 2), Ordenacao.Despesas.VENCIMENTO, null);
        assertThat(depoisDoFim.conteudo()).isEmpty();
        assertThat(depoisDoFim.totalElementos()).isEqualTo(4);
    }

    @Test
    void despesasAceitamVariasSituacoesEResumemCadaUmaSemOFiltroDeSituacao() {
        UUID escola = dados.escola();
        UUID categoria = dados.categoriaDespesa(escola);
        LocalDate outubro = LocalDate.of(2026, 10, 10);
        dados.despesa(escola, categoria, new BigDecimal("100.00"), outubro, SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, categoria, new BigDecimal("50.50"), outubro, SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, categoria, new BigDecimal("70.00"), outubro, SituacaoDespesa.PAGA, null);
        dados.despesa(escola, categoria, new BigDecimal("999.00"), outubro, SituacaoDespesa.CANCELADA, null);
        // Fora do periodo: nao entra na pagina nem no resumo.
        dados.despesa(escola, categoria, new BigDecimal("1.00"), outubro.plusMonths(1), SituacaoDespesa.PAGA, null);
        UUID outra = dados.escola();
        dados.despesa(outra, dados.categoriaDespesa(outra), new BigDecimal("5.00"), outubro, SituacaoDespesa.PAGA,
                null);
        autenticarComo("FINANCEIRO", escola);

        PaginaComResumoResponse<DespesaResponse, ResumoDespesasResponse> semCanceladas = despesaService
                .listarPaginado(List.of(SituacaoDespesa.A_PAGAR, SituacaoDespesa.PAGA), null,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new Paginacao(0, 10),
                        Ordenacao.Despesas.VALOR, null);

        assertThat(semCanceladas.conteudo()).extracting(DespesaResponse::valor)
                .containsExactly(new BigDecimal("50.50"), new BigDecimal("70.00"), new BigDecimal("100.00"));
        assertThat(semCanceladas.resumo().porSituacao())
                .extracting(ResumoDespesasResponse.TotalPorSituacao::situacao,
                        ResumoDespesasResponse.TotalPorSituacao::quantidade,
                        t -> t.valor().setScale(2))
                .containsExactly(tuple(SituacaoDespesa.A_PAGAR, 2L, new BigDecimal("150.50")),
                        tuple(SituacaoDespesa.PAGA, 1L, new BigDecimal("70.00")),
                        tuple(SituacaoDespesa.CANCELADA, 1L, new BigDecimal("999.00")));

        // Situacao sem despesa no periodo vem zerada, nunca some.
        assertThat(despesaService.listarPaginado(List.of(SituacaoDespesa.PAGA), null, LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 31), new Paginacao(0, 10), Ordenacao.Despesas.VENCIMENTO, null)
                .resumo().porSituacao())
                .extracting(ResumoDespesasResponse.TotalPorSituacao::quantidade, t -> t.valor().signum())
                .containsOnly(tuple(0L, 0));
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
    void turmasOrdenamPeloNumeroNoNomeEntreAsPaginas() {
        UUID escola = dados.escola();
        for (String nome : List.of("Turma 10", "Turma 2", "turma 1", "Turma 11", "Éxtra", "Alfa")) {
            dados.turma(escola, nome, 30);
        }

        PaginaResponse<TurmaResponse> primeira = academicoService.listarTurmas(escola, null, null, null, null,
                new Paginacao(0, 3), Ordenacao.Turmas.NOME, null);
        PaginaResponse<TurmaResponse> segunda = academicoService.listarTurmas(escola, null, null, null, null,
                new Paginacao(1, 3), Ordenacao.Turmas.NOME, null);

        // 2 antes de 10 mesmo em paginas diferentes; acento e maiuscula nao mudam o lugar.
        assertThat(primeira.conteudo()).extracting(TurmaResponse::getNome)
                .containsExactly("Alfa", "Éxtra", "turma 1");
        assertThat(segunda.conteudo()).extracting(TurmaResponse::getNome)
                .containsExactly("Turma 2", "Turma 10", "Turma 11");
        assertThat(academicoService.listarTurmas(escola, null, null, null, "TURMA 1", new Paginacao(0, 10),
                Ordenacao.Turmas.NOME, null).conteudo()).extracting(TurmaResponse::getNome)
                .containsExactly("turma 1", "Turma 10", "Turma 11");
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

        var primeira = alunoService.listarPaginado(null, null, FiltroFinanceiroAlunos.NENHUM,
                new Paginacao(0, 2), Ordenacao.Alunos.NOME, null);
        assertThat(primeira.conteudo()).extracting(AlunoResponse::nome).containsExactly("alice", "Álvaro");
        assertThat(primeira).extracting(PaginaComResumoResponse::totalElementos,
                PaginaComResumoResponse::totalPaginas).containsExactly(3L, 2);
        assertThat(alunoService.listarPaginado(null, null, FiltroFinanceiroAlunos.NENHUM, new Paginacao(1, 2),
                Ordenacao.Alunos.NOME, null).conteudo()).extracting(AlunoResponse::nome).containsExactly("Bruna");

        var semTurmaComA = alunoService.listarPaginado("a", AlunoResponse.Situacao.SEM_TURMA,
                FiltroFinanceiroAlunos.NENHUM, new Paginacao(0, 10), Ordenacao.Alunos.NOME, Direction.DESC);
        assertThat(semTurmaComA.conteudo()).extracting(AlunoResponse::nome).containsExactly("Bruna", "alice");
        // O resumo e da escola inteira: nem a busca nem a situacao mudam as contas.
        assertThat(semTurmaComA.resumo()).isEqualTo(new ResumoAlunosResponse(3, Map.of(
                AlunoResponse.Situacao.MATRICULADO, 1L,
                AlunoResponse.Situacao.TRANCADO, 0L,
                AlunoResponse.Situacao.SEM_TURMA, 2L,
                AlunoResponse.Situacao.INATIVO, 0L,
                AlunoResponse.Situacao.ANONIMIZADO, 0L), null));
    }

    @Test
    void alunosBuscamPeloNumeroDeMatricula() {
        UUID escola = dados.escola();
        dados.aluno(escola, "Carlos");
        dados.aluno(escola, "Daniela");
        autenticarComo("SECRETARIA", escola);
        AlunoResponse daniela = alunoService.listarPaginado("daniela", null, FiltroFinanceiroAlunos.NENHUM,
                new Paginacao(0, 10), Ordenacao.Alunos.NOME, null).conteudo().get(0);

        assertThat(alunoService.listarPaginado(" " + daniela.matricula().toUpperCase() + " ", null,
                FiltroFinanceiroAlunos.NENHUM, new Paginacao(0, 10), Ordenacao.Alunos.NOME, null).conteudo())
                .extracting(AlunoResponse::nome).containsExactly("Daniela");
    }

    @Test
    void alunosTrazemASituacaoFinanceiraSomadaNoBancoEFiltramPorEla() {
        UUID escola = dados.escola();
        LocalDate hoje = LocalDate.now(FUSO_ESCOLA);
        UUID ana = dados.aluno(escola, "Ana");
        UUID bia = dados.aluno(escola, "Bia");
        UUID caio = dados.aluno(escola, "Caio");
        // Ana: uma pendente ja vencida (conta como atraso antes da rotina), uma em atraso e uma a vencer.
        dados.cobranca(escola, ana, StatusCobranca.PENDENTE, hoje.minusDays(40), new BigDecimal("100.00"));
        dados.cobranca(escola, ana, StatusCobranca.EM_ATRASO, hoje.minusDays(5), new BigDecimal("50.00"));
        dados.cobranca(escola, ana, StatusCobranca.PENDENTE, hoje.plusDays(3), new BigDecimal("70.00"));
        UUID mensalidadePaga = dados.cobrancaPaga(escola, ana, new BigDecimal("300.00"), hoje, Instant.now());
        dados.classificar(mensalidadePaga, null, TipoCobranca.MENSALIDADE);
        // Bia: so uma a vencer depois da semana; a cancelada vencida nao conta.
        dados.cobranca(escola, bia, StatusCobranca.PENDENTE, hoje.plusDays(10), new BigDecimal("200.00"));
        dados.cancelada(dados.cobranca(escola, bia, StatusCobranca.PENDENTE, hoje.minusDays(30)));
        // Caio: so cancelada, entao sem cobranca.
        dados.cancelada(dados.cobranca(escola, caio, StatusCobranca.PENDENTE, hoje.plusDays(1)));
        // Outra escola nunca entra na conta.
        UUID outraEscola = dados.escola();
        dados.cobranca(outraEscola, dados.aluno(outraEscola, "Daniel"), StatusCobranca.EM_ATRASO, hoje.minusDays(90));
        autenticarComo("FINANCEIRO", escola);

        var todos = alunoService.listarPaginado(null, null, FiltroFinanceiroAlunos.NENHUM, new Paginacao(0, 10),
                Ordenacao.Alunos.EM_ABERTO, null);

        assertThat(todos.conteudo()).extracting(AlunoResponse::nome).containsExactly("Ana", "Bia", "Caio");
        FinanceiroDoAlunoResponse daAna = todos.conteudo().get(0).financeiro();
        assertThat(daAna.situacao()).isEqualTo(FinanceiroDoAlunoResponse.Situacao.EM_ATRASO);
        assertThat(daAna.valorEmAberto()).isEqualByComparingTo("220.00");
        assertThat(daAna.valorEmAtraso()).isEqualByComparingTo("150.00");
        assertThat(daAna.diasDeAtraso()).isEqualTo(40);
        assertThat(daAna.faixaAtraso()).isEqualTo(FinanceiroDoAlunoResponse.FaixaAtraso.DE_31_A_60_DIAS);
        assertThat(daAna.proximoVencimento()).isEqualTo(hoje.plusDays(3));
        assertThat(daAna.venceEm7Dias()).isTrue();
        assertThat(daAna.valorVenceEm7Dias()).isEqualByComparingTo("70.00");
        assertThat(daAna.mensalidadeNoMes()).isTrue();
        assertThat(todos.conteudo()).extracting(a -> a.financeiro().situacao()).containsExactly(
                FinanceiroDoAlunoResponse.Situacao.EM_ATRASO, FinanceiroDoAlunoResponse.Situacao.A_VENCER,
                FinanceiroDoAlunoResponse.Situacao.SEM_COBRANCA);
        assertThat(todos.resumo().financeiro().valorEmAtraso()).isEqualByComparingTo("150.00");

        var emAtraso = alunoService.listarPaginado(null, null, new FiltroFinanceiroAlunos(
                FinanceiroDoAlunoResponse.Situacao.EM_ATRASO, null, false, false), new Paginacao(0, 10),
                Ordenacao.Alunos.NOME, null);
        assertThat(emAtraso.conteudo()).extracting(AlunoResponse::nome).containsExactly("Ana");
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

        var ativos = professorService.listarPaginado(null, true, new Paginacao(0, 10), Ordenacao.Professores.NOME,
                null);
        assertThat(ativos.conteudo()).extracting(ProfessorResponse::nome).containsExactly("Bruno", "Carla");
        // Resumo da escola inteira, com a inativa; nenhum cadastro de teste informa o vinculo.
        assertThat(ativos.resumo()).isEqualTo(new ResumoProfessoresResponse(3, 2, 3));
        assertThat(professorService.listarPaginado("CAR", null, new Paginacao(0, 10), Ordenacao.Professores.NOME,
                null).conteudo()).extracting(ProfessorResponse::nome).containsExactly("Carla");
        assertThat(professorService.listarPaginado(null, null, new Paginacao(0, 10),
                Ordenacao.Professores.DATA_ADMISSAO, Direction.DESC).conteudo())
                .extracting(ProfessorResponse::nome).containsExactly("Bruno", "Ana", "Carla");

        assertThat(matriculaService.listarPaginado(turma, null, null, new Paginacao(0, 10),
                Ordenacao.Matriculas.ALUNO, null).conteudo())
                .extracting(MatriculaTurmaResponse::alunoNome).containsExactly("Ívis", "Zeca");
        assertThat(matriculaService.listarPaginado(turma, null, null, new Paginacao(0, 1),
                Ordenacao.Matriculas.DATA_MATRICULA, Direction.DESC))
                .extracting(p -> p.conteudo().get(0).alunoNome(), PaginaComResumoResponse::totalPaginas)
                .containsExactly("Ívis", 2);
    }

    @Test
    void matriculasDaTurmaTrazemOResumoSemOFiltroDeSituacao() {
        UUID escola = dados.escola();
        long turma = dados.turma(escola, "1A", 30);
        long outraTurma = dados.turma(escola, "2B", 30);
        dados.matricula(escola, dados.aluno(escola, "Ana"), turma, StatusMatricula.ATIVA, LocalDate.of(2026, 2, 1));
        dados.matricula(escola, dados.aluno(escola, "Bia"), turma, StatusMatricula.TRANCADA, LocalDate.of(2026, 2, 1));
        dados.matricula(escola, dados.aluno(escola, "Caio"), turma, StatusMatricula.CANCELADA,
                LocalDate.of(2026, 2, 1));
        dados.matricula(escola, dados.aluno(escola, "Davi"), outraTurma, StatusMatricula.ATIVA,
                LocalDate.of(2026, 2, 1));
        dados.aluno(escola, "Eva");
        // Inativado pela escola: nao recebe matricula, entao nao conta como "para matricular".
        jdbc.update("UPDATE alunos SET ativo = false, inativado_em = now() WHERE id_aluno = ?",
                dados.aluno(escola, "Gil"));
        UUID outra = dados.escola();
        dados.aluno(outra, "De fora");
        autenticarComo("SECRETARIA", escola);

        var trancadas = matriculaService.listarPaginado(turma, null, StatusMatricula.TRANCADA, new Paginacao(0, 10),
                Ordenacao.Matriculas.ALUNO, null);

        assertThat(trancadas.conteudo()).extracting(MatriculaTurmaResponse::alunoNome).containsExactly("Bia");
        // Os contadores da turma inteira, e quem ainda pode entrar: o Davi (de outra turma) e a Eva (sem turma);
        // o Gil, inativo, fica de fora.
        assertThat(trancadas.resumo()).isEqualTo(new ResumoMatriculasResponse(3, Map.of(
                StatusMatricula.ATIVA, 1L, StatusMatricula.TRANCADA, 1L, StatusMatricula.CANCELADA, 1L,
                StatusMatricula.CONCLUIDA, 0L), 2L));
        // Por aluno, o "para matricular" nao faz sentido.
        assertThat(matriculaService.listarPaginado(null, dados.aluno(escola, "Fabi"), null, new Paginacao(0, 10),
                Ordenacao.Matriculas.ALUNO, null).resumo().paraMatricular()).isNull();
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
        assertThat(paymentRoutingService.listarFaturas(escola, Set.of(StatusCobranca.PAGA), new Paginacao(0, 1),
                Ordenacao.Faturas.COMPETENCIA, Direction.ASC))
                .extracting(p -> p.conteudo().get(0).competencia(), PaginaResponse::totalElementos)
                .containsExactly("2026-08", 2L);
    }

    @Test
    void faturasFiltramPorMaisDeUmaSituacao() {
        UUID escola = dados.escola();
        UUID master = dados.usuario(escola, "MASTER", "Admin");
        dados.fatura(escola, master, "2026-07", new BigDecimal("199.90"), StatusCobranca.CANCELADA);
        dados.fatura(escola, master, "2026-08", new BigDecimal("199.90"), StatusCobranca.PAGA);
        // Vencida e ainda PENDENTE no banco: a rotina diaria nao rodou.
        dados.fatura(escola, master, "2026-09", new BigDecimal("199.90"), StatusCobranca.PENDENTE);
        dados.fatura(escola, master, "2026-10", new BigDecimal("199.90"), StatusCobranca.EM_ATRASO);
        autenticarComo("MASTER", escola);

        // As anteriores da tela Plano e faturas: pagas e canceladas, da mais recente.
        assertThat(paymentRoutingService.listarMinhasFaturas(Set.of(StatusCobranca.PAGA, StatusCobranca.CANCELADA),
                new Paginacao(0, 10), Ordenacao.Faturas.COMPETENCIA, null).conteudo())
                .extracting(FaturaPlataformaResponse::competencia)
                .containsExactly("2026-08", "2026-07");
        // A proxima: a em aberto que vence primeiro, inclusive a que segue PENDENTE.
        assertThat(paymentRoutingService.listarMinhasFaturas(Set.of(StatusCobranca.PENDENTE, StatusCobranca.EM_ATRASO),
                new Paginacao(0, 1), Ordenacao.Faturas.VENCIMENTO, null))
                .extracting(p -> p.conteudo().get(0).competencia(), PaginaResponse::totalElementos)
                .containsExactly("2026-09", 2L);
        assertThat(paymentRoutingService.listarMinhasFaturas(Set.of(), new Paginacao(0, 10),
                Ordenacao.Faturas.COMPETENCIA, null).totalElementos()).isEqualTo(4);
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
