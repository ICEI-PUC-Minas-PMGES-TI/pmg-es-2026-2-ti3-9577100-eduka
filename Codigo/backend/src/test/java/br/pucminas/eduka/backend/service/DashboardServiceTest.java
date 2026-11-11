package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Aula;
import br.pucminas.eduka.backend.domain.Avaliacao;
import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.DiaSemana;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.HorarioTurma;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.TipoAvaliacao;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.domain.Turno;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.DashboardEscolaResponse;
import br.pucminas.eduka.backend.dto.DashboardProfessorResponse;
import br.pucminas.eduka.backend.dto.DashboardProfessorResponse.AulaDaSemana;
import br.pucminas.eduka.backend.dto.DashboardProfessorResponse.AvaliacaoResumo;
import br.pucminas.eduka.backend.dto.DashboardSecretariaResponse;
import br.pucminas.eduka.backend.dto.DashboardSecretariaResponse.OcupacaoTurma;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AulaRepository;
import br.pucminas.eduka.backend.repository.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.CursoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.HorarioTurmaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.NotaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/** Contas dos tres dashboards, com "hoje" fixo em 10/10/2026. */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 10);

    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private ProfessorRepository professorRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private DisciplinaRepository disciplinaRepository;
    @Mock
    private PeriodoLetivoRepository periodoLetivoRepository;
    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private MatriculaTurmaRepository matriculaRepository;
    @Mock
    private AlocacaoProfessorRepository alocacaoRepository;
    @Mock
    private AvaliacaoRepository avaliacaoRepository;
    @Mock
    private NotaRepository notaRepository;
    @Mock
    private HorarioTurmaRepository horarioRepository;
    @Mock
    private AulaRepository aulaRepository;

    private DashboardService service;

    private Turma turmaA;
    private Turma turmaB;
    private Disciplina matematica;
    private Disciplina fisica;

    @BeforeEach
    void setUp() {
        // 15h UTC = 12h em Sao Paulo: o mesmo dia nos dois fusos.
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T15:00:00Z"), ZoneOffset.UTC);
        service = new DashboardService(alunoRepository, professorRepository, usuarioRepository, cursoRepository,
                disciplinaRepository, periodoLetivoRepository, turmaRepository, matriculaRepository,
                alocacaoRepository, avaliacaoRepository, notaRepository, horarioRepository, aulaRepository,
                new IsolamentoEscola(), clock);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "MASTER", ESCOLA), null, "ROLE_MASTER"));

        matematica = Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build();
        fisica = Disciplina.builder().id(11L).nome("Fisica").cargaHoraria(60).build();
        turmaA = turma(1L, "1A", 2, true);
        turmaB = turma(2L, "2B", 4, true);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Turma turma(Long id, String nome, int vagas, boolean ativa) {
        return Turma.builder().id(id).nome(nome).vagas(vagas).ativa(ativa).turno(Turno.MATUTINO)
                .periodoLetivo(PeriodoLetivo.builder().id(5L).nome("2026/2").build())
                .curso(Curso.builder().id(7L).nome("Ensino Medio").build())
                .build();
    }

    private static HorarioTurma horario(AlocacaoProfessor alocacao, DiaSemana dia, String inicio) {
        LocalTime hora = LocalTime.parse(inicio);
        return HorarioTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA).alocacao(alocacao).diaSemana(dia)
                .horaInicio(hora).horaFim(hora.plusMinutes(50)).build();
    }

    private static Aluno aluno() {
        return Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).build();
    }

    private static MatriculaTurma matricula(Aluno aluno, Turma turma, StatusMatricula status, LocalDate data) {
        return MatriculaTurma.builder().id(UUID.randomUUID()).aluno(aluno).turma(turma).status(status)
                .dataMatricula(data).build();
    }

    private Avaliacao avaliacao(Turma turma, Disciplina disciplina, String titulo, LocalDate data, String valor) {
        return Avaliacao.builder().id(UUID.randomUUID()).escolaId(ESCOLA).turma(turma).disciplina(disciplina)
                .tipo(TipoAvaliacao.PROVA).titulo(titulo).data(data).valor(new BigDecimal(valor)).build();
    }

    /** Somas nulas, como o banco devolve quando nao ha nota. */
    private static NotaRepository.Aproveitamento aproveitamento(long total, String obtido, String possivel) {
        return new NotaRepository.Aproveitamento() {
            public long getTotal() {
                return total;
            }

            public BigDecimal getObtido() {
                return obtido == null ? null : new BigDecimal(obtido);
            }

            public BigDecimal getPossivel() {
                return possivel == null ? null : new BigDecimal(possivel);
            }
        };
    }

    private static NotaRepository.NotaLancada lancada(Avaliacao avaliacao, Aluno aluno) {
        return new NotaRepository.NotaLancada() {
            public UUID getAvaliacaoId() {
                return avaliacao.getId();
            }

            public UUID getAlunoId() {
                return aluno.getId();
            }
        };
    }

    private static MatriculaTurmaRepository.AtivasNaTurma ativas(Turma turma, long quantidade) {
        return new MatriculaTurmaRepository.AtivasNaTurma() {
            public Long getTurmaId() {
                return turma.getId();
            }

            public long getQuantidade() {
                return quantidade;
            }
        };
    }

    private static MatriculaTurmaRepository.PorSituacao naSituacao(StatusMatricula status, long quantidade) {
        return new MatriculaTurmaRepository.PorSituacao() {
            public StatusMatricula getStatus() {
                return status;
            }

            public long getQuantidade() {
                return quantidade;
            }
        };
    }

    // As contagens vem do banco (o MatriculaTurmaRepository e conferido no DashboardBancoRealTest);
    // aqui, as contas feitas com elas.

    @Test
    void escolaContaPessoasEstruturaOcupacaoEAproveitamento() {
        // 3 alunos: 2 com matricula ativa (uma em cada turma) e 1 so com matricula concluida.
        when(alunoRepository.countByEscolaIdAndAnonimizadoEmIsNull(ESCOLA)).thenReturn(3L);
        when(matriculaRepository.contarAlunosComMatriculaAtiva(ESCOLA)).thenReturn(2L);
        Turma encerrada = turma(3L, "3C", 30, false);
        when(matriculaRepository.contarAtivasPorTurma(ESCOLA)).thenReturn(List.of(ativas(turmaA, 1), ativas(turmaB, 1)));
        when(professorRepository.countByEscolaId(ESCOLA)).thenReturn(2L);
        when(professorRepository.countByEscolaIdAndAtivoTrue(ESCOLA)).thenReturn(1L);
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA, PerfilUsuario.SECRETARIA)).thenReturn(List.of(
                Usuario.builder().ativo(true).build(), Usuario.builder().ativo(false).build()));
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA, PerfilUsuario.FINANCEIRO)).thenReturn(List.of());
        when(turmaRepository.findAllByEscolaId(ESCOLA)).thenReturn(List.of(turmaA, turmaB, encerrada));
        when(periodoLetivoRepository.findAllByEscolaId(ESCOLA)).thenReturn(List.of(
                PeriodoLetivo.builder().nome("2026/1").ativo(true).dataInicio(LocalDate.of(2026, 2, 1))
                        .dataFim(LocalDate.of(2026, 7, 1)).build(),
                PeriodoLetivo.builder().nome("2026/2").ativo(true).dataInicio(LocalDate.of(2026, 8, 1))
                        .dataFim(LocalDate.of(2026, 12, 18)).build()));
        when(cursoRepository.countByEscolaId(ESCOLA)).thenReturn(1L);
        when(disciplinaRepository.countByEscolaId(ESCOLA)).thenReturn(2L);
        when(avaliacaoRepository.countByEscolaId(ESCOLA)).thenReturn(2L);
        // 3 notas somando 20 de 25 possiveis = 80%.
        when(notaRepository.somarAproveitamento(ESCOLA)).thenReturn(aproveitamento(3, "20", "25"));

        DashboardEscolaResponse resposta = service.escola();

        assertThat(resposta.pessoas()).isEqualTo(new DashboardEscolaResponse.Pessoas(3, 2, 1, 1, 1, 1, 0));
        assertThat(resposta.estrutura()).isEqualTo(new DashboardEscolaResponse.Estrutura(1, 2, 2, 3, "2026/2"));
        // Vagas so das turmas ativas: 2 + 4, com 2 matriculas ativas = 33%.
        assertThat(resposta.ocupacao()).isEqualTo(new DashboardEscolaResponse.Ocupacao(6, 2, 33));
        assertThat(resposta.desempenho()).isEqualTo(new DashboardEscolaResponse.Desempenho(2, 3, 80));
    }

    @Test
    void escolaSemVagasNemNotasDevolvePercentuaisNulos() {
        // As contagens sem stub ja voltam 0 e as listas, vazias.
        when(usuarioRepository.findAllByEscolaIdAndPerfil(eq(ESCOLA), any())).thenReturn(List.of());
        when(turmaRepository.findAllByEscolaId(ESCOLA)).thenReturn(List.of());
        when(periodoLetivoRepository.findAllByEscolaId(ESCOLA)).thenReturn(List.of());
        when(notaRepository.somarAproveitamento(ESCOLA)).thenReturn(aproveitamento(0, null, null));

        DashboardEscolaResponse resposta = service.escola();

        assertThat(resposta.ocupacao().percentual()).isNull();
        assertThat(resposta.desempenho().aproveitamento()).isNull();
        assertThat(resposta.estrutura().periodoLetivoAtual()).isNull();
    }

    @Test
    void secretariaResumeMatriculasEVagasDaMaisCheiaParaAMaisVazia() {
        // 4 alunos: 3 ativos (2 na 1A, 1 na 2B) e 1 so com matricula cancelada.
        when(alunoRepository.countByEscolaIdAndAnonimizadoEmIsNull(ESCOLA)).thenReturn(4L);
        when(matriculaRepository.contarPorSituacao(ESCOLA)).thenReturn(List.of(
                naSituacao(StatusMatricula.ATIVA, 3), naSituacao(StatusMatricula.CANCELADA, 1)));
        // Ultimos 30 dias incluem o 30o dia para tras.
        when(matriculaRepository.countByEscolaIdAndDataMatriculaGreaterThanEqual(ESCOLA, HOJE.minusDays(30)))
                .thenReturn(2L);
        when(matriculaRepository.contarAlunosComMatriculaAtiva(ESCOLA)).thenReturn(3L);
        when(matriculaRepository.contarAtivasPorTurma(ESCOLA)).thenReturn(List.of(ativas(turmaA, 2), ativas(turmaB, 1)));
        when(turmaRepository.findAllByEscolaId(ESCOLA)).thenReturn(List.of(turmaB, turmaA));

        DashboardSecretariaResponse resposta = service.secretaria();

        assertThat(resposta.matriculasPorSituacao()).containsEntry(StatusMatricula.ATIVA, 3L)
                .containsEntry(StatusMatricula.CANCELADA, 1L)
                .containsEntry(StatusMatricula.TRANCADA, 0L)
                .containsEntry(StatusMatricula.CONCLUIDA, 0L);
        assertThat(resposta.matriculasUltimos30Dias()).isEqualTo(2);
        assertThat(resposta.alunosSemTurma()).isEqualTo(1);
        // 1A: 2 de 2 (lotada); 2B: 1 de 4.
        assertThat(resposta.turmas()).extracting(OcupacaoTurma::turmaNome).containsExactly("1A", "2B");
        assertThat(resposta.vagas()).isEqualTo(new DashboardSecretariaResponse.Vagas(6, 3, 3, 1));
    }

    @Test
    void professorVeProximasAvaliacoesENotasPendentesSoDasDisciplinasDele() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "PROFESSOR", ESCOLA), null, "ROLE_PROFESSOR"));
        Professor professor = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA).usuarioId(USUARIO).build();
        when(professorRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.of(professor));
        // Leciona Matematica no 1A e no 2B; Fisica do 1A e de outro professor.
        when(alocacaoRepository.findAllComTurmaEDisciplinaByEscolaIdAndProfessorId(ESCOLA, professor.getId()))
                .thenReturn(List.of(
                        AlocacaoProfessor.builder().professor(professor).turma(turmaA).disciplina(matematica).build(),
                        AlocacaoProfessor.builder().professor(professor).turma(turmaB).disciplina(matematica).build()));
        Aluno ana = aluno();
        Aluno bruno = aluno();
        Aluno trancado = aluno();
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 1L)).thenReturn(List.of(
                matricula(ana, turmaA, StatusMatricula.ATIVA, HOJE),
                matricula(bruno, turmaA, StatusMatricula.ATIVA, HOJE),
                matricula(trancado, turmaA, StatusMatricula.TRANCADA, HOJE)));
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 2L)).thenReturn(List.of(
                matricula(ana, turmaB, StatusMatricula.ATIVA, HOJE)));

        Avaliacao passada = avaliacao(turmaA, matematica, "Prova 1", HOJE.minusDays(10), "10");
        Avaliacao passadaCompleta = avaliacao(turmaB, matematica, "Lista", HOJE.minusDays(3), "2");
        Avaliacao hoje = avaliacao(turmaA, matematica, "Trabalho", HOJE, "5");
        Avaliacao proxima = avaliacao(turmaB, matematica, "Prova 2", HOJE.plusDays(20), "10");
        Avaliacao longe = avaliacao(turmaA, matematica, "Prova 3", HOJE.plusDays(45), "10");
        Avaliacao deOutro = avaliacao(turmaA, fisica, "Fisica", HOJE.plusDays(1), "10");
        when(avaliacaoRepository.findAllByEscolaIdAndTurmaIdIn(eq(ESCOLA), anyCollection()))
                .thenReturn(List.of(longe, proxima, hoje, passadaCompleta, passada, deOutro));
        lenient().when(notaRepository.listarLancadas(eq(ESCOLA), anyCollection())).thenReturn(List.of(
                lancada(passada, ana), lancada(passada, trancado), lancada(passadaCompleta, ana)));

        DashboardProfessorResponse resposta = service.professor();

        assertThat(resposta.turmas()).isEqualTo(2);
        assertThat(resposta.disciplinas()).isEqualTo(1);
        assertThat(resposta.alunos()).isEqualTo(2);
        assertThat(resposta.avaliacoes()).isEqualTo(5);
        assertThat(resposta.proximasAvaliacoes()).extracting(AvaliacaoResumo::titulo)
                .containsExactly("Trabalho", "Prova 2");
        // Prova 1: falta o Bruno (o trancado nao conta). O trabalho de hoje ainda nao aconteceu: so em proximas.
        assertThat(resposta.avaliacoesPendentes()).extracting(AvaliacaoResumo::titulo, AvaliacaoResumo::notasFaltando)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Prova 1", 1L));
    }

    @Test
    void professorVeAsAulasDaSemanaComAChamadaFeitaOuNao() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "PROFESSOR", ESCOLA), null, "ROLE_PROFESSOR"));
        Professor professor = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA).usuarioId(USUARIO).build();
        when(professorRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.of(professor));
        Turma encerrada = turma(3L, "3C", 10, false);
        AlocacaoProfessor noA = AlocacaoProfessor.builder().id(UUID.randomUUID()).professor(professor).turma(turmaA)
                .disciplina(matematica).build();
        AlocacaoProfessor naEncerrada = AlocacaoProfessor.builder().id(UUID.randomUUID()).professor(professor)
                .turma(encerrada).disciplina(matematica).build();
        when(alocacaoRepository.findAllComTurmaEDisciplinaByEscolaIdAndProfessorId(ESCOLA, professor.getId()))
                .thenReturn(List.of(noA, naEncerrada));
        // Aula dupla na terca e uma na quinta; a turma encerrada nem entra na consulta da grade.
        when(horarioRepository.findAllByEscolaIdAndAlocacao_IdIn(eq(ESCOLA), anyCollection())).thenReturn(List.of(
                horario(noA, DiaSemana.QUINTA, "07:00"),
                horario(noA, DiaSemana.TERCA, "07:50"),
                horario(noA, DiaSemana.TERCA, "07:00")));
        // Hoje e sabado, 10/10: a semana vai de 05/10 a 10/10. Na terca so uma chamada foi registrada.
        when(aulaRepository.findAllByEscolaIdAndTurmaIdInAndDataBetween(ESCOLA, java.util.Set.of(1L),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10)))
                .thenReturn(List.of(Aula.builder().turma(turmaA).disciplina(matematica)
                        .data(LocalDate.of(2026, 10, 6)).build()));

        List<AulaDaSemana> aulas = service.professor().aulasDaSemana();

        assertThat(aulas).extracting(AulaDaSemana::data, AulaDaSemana::horaInicio, AulaDaSemana::chamadaFeita)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 10, 6), LocalTime.of(7, 0), true),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 10, 6), LocalTime.of(7, 50), false),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 10, 8), LocalTime.of(7, 0), false));
        assertThat(aulas).allSatisfy(a -> assertThat(a.turmaNome()).isEqualTo("1A"));
    }

    @Test
    void professorSemAlocacaoRecebeTudoZerado() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "PROFESSOR", ESCOLA), null, "ROLE_PROFESSOR"));
        Professor professor = Professor.builder().id(UUID.randomUUID()).build();
        when(professorRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.of(professor));

        DashboardProfessorResponse resposta = service.professor();

        assertThat(resposta.turmas()).isZero();
        assertThat(resposta.proximasAvaliacoes()).isEmpty();
    }

    @Test
    void loginSemCadastroDeProfessorResponde404() {
        when(professorRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.professor()).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
