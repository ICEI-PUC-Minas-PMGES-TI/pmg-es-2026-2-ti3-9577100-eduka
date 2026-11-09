package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Avaliacao;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Nota;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.TipoAvaliacao;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.dto.AtualizarAvaliacaoRequest;
import br.pucminas.eduka.backend.dto.AvaliacaoRequest;
import br.pucminas.eduka.backend.dto.AvaliacaoResponse;
import br.pucminas.eduka.backend.dto.LancarNotasRequest;
import br.pucminas.eduka.backend.dto.LancarNotasRequest.NotaLancamento;
import br.pucminas.eduka.backend.dto.NotaAlunoResponse;
import br.pucminas.eduka.backend.exception.AvaliacaoComNotasException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.NotaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Regras de avaliacao e nota: o professor so mexe no que esta alocado a ele,
 * a nota respeita o valor da avaliacao e so aluno com matricula ativa recebe
 * nota.
 */
@ExtendWith(MockitoExtension.class)
class AvaliacaoServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();

    @Mock
    private AvaliacaoRepository avaliacaoRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private NotaRepository notaRepository;
    @Mock
    private ProfessorRepository professorRepository;
    @Mock
    private AlocacaoProfessorRepository alocacaoRepository;
    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private DisciplinaRepository disciplinaRepository;
    @Mock
    private MatriculaTurmaRepository matriculaRepository;
    @Mock
    private PessoaRepository pessoaRepository;

    private AvaliacaoService service;

    private Professor professor;
    private Turma turma;
    private Disciplina disciplina;

    @BeforeEach
    void setUp() {
        service = new AvaliacaoService(avaliacaoRepository, notaRepository, professorRepository, alocacaoRepository,
                turmaRepository, disciplinaRepository, matriculaRepository, pessoaRepository,
                new IsolamentoEscola(), auditoriaService);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "PROFESSOR", ESCOLA), null, "ROLE_PROFESSOR"));

        professor = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA).usuarioId(USUARIO).ativo(true)
                .build();
        turma = Turma.builder().id(1L).nome("1A").build();
        disciplina = Disciplina.builder().id(10L).nome("Matematica").build();
        when(professorRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.of(professor));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void alocado(boolean alocado) {
        when(alocacaoRepository.existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(ESCOLA, professor.getId(),
                1L, 10L)).thenReturn(alocado);
    }

    private Avaliacao avaliacao(String titulo, LocalDate data, String valor) {
        return Avaliacao.builder().id(UUID.randomUUID()).escolaId(ESCOLA).turma(turma).disciplina(disciplina)
                .professor(professor).tipo(TipoAvaliacao.PROVA).titulo(titulo).data(data)
                .valor(new BigDecimal(valor)).build();
    }

    private Avaliacao avaliacaoExistente(String valor) {
        Avaliacao avaliacao = avaliacao("Prova 1", LocalDate.of(2026, 10, 10), valor);
        when(avaliacaoRepository.findByIdAndEscolaId(avaliacao.getId(), ESCOLA)).thenReturn(Optional.of(avaliacao));
        alocado(true);
        return avaliacao;
    }

    private static Aluno aluno() {
        return Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(UUID.randomUUID())
                .matricula("2026-" + UUID.randomUUID().toString().substring(0, 4)).build();
    }

    private MatriculaTurma matricula(Aluno aluno, StatusMatricula status) {
        return MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA).aluno(aluno).turma(turma)
                .status(status).build();
    }

    private static Pessoa pessoa(Aluno aluno, String nome) {
        return Pessoa.builder().id(aluno.getPessoaId()).escolaId(ESCOLA).nome(nome).ativo(true).build();
    }

    private static AvaliacaoRequest criarRequest() {
        return new AvaliacaoRequest(1L, 10L, TipoAvaliacao.PROVA, " Prova 1 ", " ", LocalDate.of(2026, 10, 10),
                new BigDecimal("10"));
    }

    @Test
    void criarEmDisciplinaNaoAlocadaRespondeNaoEncontradoSemGravar() {
        alocado(false);

        assertThatThrownBy(() -> service.criar(criarRequest()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Turma ou disciplina nao alocada ao professor");
        verify(avaliacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void criarGravaEscolaEProfessorDoToken() {
        alocado(true);
        when(turmaRepository.getReferenceById(1L)).thenReturn(turma);
        when(disciplinaRepository.getReferenceById(10L)).thenReturn(disciplina);
        when(avaliacaoRepository.saveAndFlush(any(Avaliacao.class))).thenAnswer(inv -> {
            Avaliacao a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            when(avaliacaoRepository.findByIdAndEscolaId(a.getId(), ESCOLA)).thenReturn(Optional.of(a));
            return a;
        });

        AvaliacaoResponse resposta = service.criar(criarRequest());

        ArgumentCaptor<Avaliacao> captor = ArgumentCaptor.forClass(Avaliacao.class);
        verify(avaliacaoRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEscolaId()).isEqualTo(ESCOLA);
        assertThat(captor.getValue().getProfessor()).isEqualTo(professor);
        assertThat(resposta.titulo()).isEqualTo("Prova 1");
        assertThat(resposta.descricao()).isNull();
        assertThat(resposta.turmaNome()).isEqualTo("1A");
        assertThat(resposta.notasLancadas()).isZero();
    }

    @Test
    void listarOrdenaPorDataEContaAsNotas() {
        alocado(true);
        Avaliacao trabalho = avaliacao("Trabalho", LocalDate.of(2026, 11, 1), "5");
        Avaliacao prova = avaliacao("Prova", LocalDate.of(2026, 10, 1), "10");
        when(avaliacaoRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaId(ESCOLA, 1L, 10L))
                .thenReturn(List.of(trabalho, prova));
        NotaRepository.NotasPorAvaliacao contagem = new NotaRepository.NotasPorAvaliacao() {
            public UUID getAvaliacaoId() {
                return prova.getId();
            }

            public long getTotal() {
                return 3;
            }
        };
        when(notaRepository.contarPorAvaliacao(eq(ESCOLA), anyCollection())).thenReturn(List.of(contagem));

        List<AvaliacaoResponse> resposta = service.listar(1L, 10L);

        assertThat(resposta).extracting(AvaliacaoResponse::titulo).containsExactly("Prova", "Trabalho");
        assertThat(resposta).extracting(AvaliacaoResponse::notasLancadas).containsExactly(3L, 0L);
    }

    @Test
    void avaliacaoDeDisciplinaQueOProfessorNaoLecionaMaisEhTratadaComoInexistente() {
        Avaliacao avaliacao = avaliacao("Prova 1", LocalDate.of(2026, 10, 10), "10");
        when(avaliacaoRepository.findByIdAndEscolaId(avaliacao.getId(), ESCOLA)).thenReturn(Optional.of(avaliacao));
        alocado(false);

        assertThatThrownBy(() -> service.listarNotas(avaliacao.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Avaliacao nao encontrada");
    }

    @Test
    void atualizarComValorAbaixoDeNotaLancadaRecusa() {
        Avaliacao avaliacao = avaliacaoExistente("10");
        Nota nota = Nota.builder().avaliacao(avaliacao).aluno(aluno()).valor(new BigDecimal("8")).build();
        when(notaRepository.findAllByEscolaIdAndAvaliacaoId(ESCOLA, avaliacao.getId())).thenReturn(List.of(nota));

        assertThatThrownBy(() -> service.atualizar(avaliacao.getId(), new AtualizarAvaliacaoRequest(
                TipoAvaliacao.PROVA, "Prova 1", null, LocalDate.of(2026, 10, 10), new BigDecimal("7.5"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void excluirComNotaLancadaResponde409() {
        Avaliacao avaliacao = avaliacaoExistente("10");
        when(notaRepository.existsByEscolaIdAndAvaliacaoId(ESCOLA, avaliacao.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(avaliacao.getId())).isInstanceOf(AvaliacaoComNotasException.class);
        verify(avaliacaoRepository, never()).delete(any());
    }

    @Test
    void excluirSemNotasApaga() {
        Avaliacao avaliacao = avaliacaoExistente("10");

        service.excluir(avaliacao.getId());

        verify(avaliacaoRepository).delete(avaliacao);
    }

    @Test
    void notaAcimaDoValorDaAvaliacaoRecusaOLoteInteiro() {
        Avaliacao avaliacao = avaliacaoExistente("10");

        assertThatThrownBy(() -> service.lancarNotas(avaliacao.getId(), new LancarNotasRequest(
                List.of(new NotaLancamento(UUID.randomUUID(), new BigDecimal("10.5"), null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que o valor");
        verify(notaRepository, never()).save(any());
    }

    @Test
    void alunoRepetidoNoLoteRecusa() {
        Avaliacao avaliacao = avaliacaoExistente("10");
        UUID alunoId = UUID.randomUUID();

        assertThatThrownBy(() -> service.lancarNotas(avaliacao.getId(), new LancarNotasRequest(List.of(
                new NotaLancamento(alunoId, BigDecimal.ONE, null), new NotaLancamento(alunoId, BigDecimal.TWO, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetido");
    }

    @Test
    void alunoSemMatriculaAtivaNaTurmaNaoRecebeNota() {
        Avaliacao avaliacao = avaliacaoExistente("10");
        Aluno trancado = aluno();
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 1L))
                .thenReturn(List.of(matricula(trancado, StatusMatricula.TRANCADA)));

        assertThatThrownBy(() -> service.lancarNotas(avaliacao.getId(), new LancarNotasRequest(
                List.of(new NotaLancamento(trancado.getId(), BigDecimal.ONE, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("matricula ativa");
        verify(notaRepository, never()).save(any());
    }

    @Test
    void lancarCriaAtualizaEApagaNotas() {
        Avaliacao avaliacao = avaliacaoExistente("10");
        Aluno ana = aluno();
        Aluno bruno = aluno();
        Aluno carla = aluno();
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 1L)).thenReturn(List.of(
                matricula(ana, StatusMatricula.ATIVA), matricula(bruno, StatusMatricula.ATIVA),
                matricula(carla, StatusMatricula.ATIVA)));
        Nota notaBruno = Nota.builder().id(UUID.randomUUID()).avaliacao(avaliacao).aluno(bruno)
                .valor(new BigDecimal("5")).build();
        Nota notaCarla = Nota.builder().id(UUID.randomUUID()).avaliacao(avaliacao).aluno(carla)
                .valor(new BigDecimal("7")).build();
        when(notaRepository.findAllByEscolaIdAndAvaliacaoId(ESCOLA, avaliacao.getId()))
                .thenReturn(List.of(notaBruno, notaCarla));

        service.lancarNotas(avaliacao.getId(), new LancarNotasRequest(List.of(
                new NotaLancamento(ana.getId(), new BigDecimal("9.5"), " Otima "),
                new NotaLancamento(bruno.getId(), new BigDecimal("6"), null),
                new NotaLancamento(carla.getId(), null, null))));

        ArgumentCaptor<Nota> salvas = ArgumentCaptor.forClass(Nota.class);
        verify(notaRepository, org.mockito.Mockito.times(2)).save(salvas.capture());
        Nota nova = salvas.getAllValues().get(0);
        assertThat(nova.getAluno()).isEqualTo(ana);
        assertThat(nova.getEscolaId()).isEqualTo(ESCOLA);
        assertThat(nova.getValor()).isEqualByComparingTo("9.5");
        assertThat(nova.getObservacao()).isEqualTo("Otima");
        assertThat(salvas.getAllValues().get(1)).isSameAs(notaBruno);
        assertThat(notaBruno.getValor()).isEqualByComparingTo("6");
        assertThat(notaBruno.getAtualizadoEm()).isNotNull();
        verify(notaRepository).delete(notaCarla);
        // Uma linha por nota que mudou, com o id da nota.
        verify(auditoriaService).registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.NOTA, nova.getId());
        verify(auditoriaService).registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.NOTA, notaBruno.getId());
        verify(auditoriaService).registrar(OperacaoAuditoria.EXCLUIR, EntidadeAuditada.NOTA, notaCarla.getId());
    }

    @Test
    void reenviarODiarioIgualNaoEntraNoLog() {
        Avaliacao avaliacao = avaliacaoExistente("10");
        Aluno ana = aluno();
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 1L))
                .thenReturn(List.of(matricula(ana, StatusMatricula.ATIVA)));
        when(notaRepository.findAllByEscolaIdAndAvaliacaoId(ESCOLA, avaliacao.getId())).thenReturn(List.of(
                Nota.builder().id(UUID.randomUUID()).avaliacao(avaliacao).aluno(ana).valor(new BigDecimal("8.0"))
                        .observacao("Boa").build()));

        service.lancarNotas(avaliacao.getId(), new LancarNotasRequest(
                List.of(new NotaLancamento(ana.getId(), new BigDecimal("8"), " Boa "))));

        verify(notaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any());
    }

    @Test
    void rascunhoGuardaSemPublicarELancarPublica() {
        Avaliacao avaliacao = avaliacaoExistente("10");
        Aluno ana = aluno();
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 1L))
                .thenReturn(List.of(matricula(ana, StatusMatricula.ATIVA)));
        List<NotaLancamento> notas = List.of(new NotaLancamento(ana.getId(), new BigDecimal("7"), null));

        service.lancarNotas(avaliacao.getId(), new LancarNotasRequest(notas, true));
        assertThat(avaliacao.isNotasPublicadas()).isFalse();

        service.lancarNotas(avaliacao.getId(), new LancarNotasRequest(notas, false));
        assertThat(avaliacao.isNotasPublicadas()).isTrue();
        verify(avaliacaoRepository, org.mockito.Mockito.times(2)).save(avaliacao);
        // Publicar entra no log uma vez, quando o rascunho vira boletim.
        verify(auditoriaService).registrar(OperacaoAuditoria.PUBLICAR_NOTAS, EntidadeAuditada.AVALIACAO,
                avaliacao.getId());
    }

    @Test
    void diarioTrazAtivosSemNotaEQuemTemNotaMasTrancou() {
        Avaliacao avaliacao = avaliacaoExistente("10");
        Aluno ana = aluno();
        Aluno bruno = aluno();
        Aluno cancelado = aluno();
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 1L)).thenReturn(List.of(
                matricula(bruno, StatusMatricula.ATIVA), matricula(ana, StatusMatricula.TRANCADA),
                matricula(cancelado, StatusMatricula.CANCELADA)));
        when(notaRepository.findAllByEscolaIdAndAvaliacaoId(ESCOLA, avaliacao.getId())).thenReturn(List.of(
                Nota.builder().avaliacao(avaliacao).aluno(ana).valor(new BigDecimal("8")).build()));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA)))
                .thenReturn(List.of(pessoa(ana, "Ana"), pessoa(bruno, "Bruno")));

        List<NotaAlunoResponse> diario = service.listarNotas(avaliacao.getId());

        assertThat(diario).extracting(NotaAlunoResponse::alunoNome).containsExactly("Ana", "Bruno");
        assertThat(diario.get(0).situacaoMatricula()).isEqualTo(StatusMatricula.TRANCADA);
        assertThat(diario.get(0).valor()).isEqualByComparingTo("8");
        assertThat(diario.get(1).situacaoMatricula()).isEqualTo(StatusMatricula.ATIVA);
        assertThat(diario.get(1).valor()).isNull();
    }

    @Test
    void loginSemCadastroDeProfessorResponde404() {
        when(professorRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(1L, 10L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Professor nao encontrado");
        verify(avaliacaoRepository, never()).findAllByEscolaIdAndTurmaIdAndDisciplinaId(any(), any(), any());
    }
}
