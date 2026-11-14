package br.pucminas.eduka.backend.service.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.Avaliacao;
import br.pucminas.eduka.backend.domain.academico.Curso;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.Nota;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.TipoAvaliacao;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.dto.academico.AvaliacaoDoAlunoResponse;
import br.pucminas.eduka.backend.dto.academico.BoletimTurmaResponse.BoletimDisciplina;
import br.pucminas.eduka.backend.dto.academico.BoletimTurmaResponse;
import br.pucminas.eduka.backend.dto.academico.TurmaDoAlunoResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository.FrequenciaDaDisciplina;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.NotaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Boletim, turmas e agenda do aluno: sempre do aluno do token, com as notas
 * dele e os totais de pontos por disciplina.
 */
@ExtendWith(MockitoExtension.class)
class PortalAlunoServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();

    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private MatriculaTurmaRepository matriculaRepository;
    @Mock
    private AlocacaoProfessorRepository alocacaoRepository;
    @Mock
    private AvaliacaoRepository avaliacaoRepository;
    @Mock
    private NotaRepository notaRepository;
    @Mock
    private FrequenciaRepository frequenciaRepository;
    @Mock
    private PessoaRepository pessoaRepository;

    private PortalAlunoService service;

    private Aluno aluno;
    private Turma turma;
    private Disciplina matematica;
    private Disciplina fisica;

    @BeforeEach
    void setUp() {
        service = new PortalAlunoService(alunoRepository, matriculaRepository, alocacaoRepository,
                avaliacaoRepository, notaRepository, frequenciaRepository, pessoaRepository, new IsolamentoEscola());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "ALUNO", ESCOLA), null, "ROLE_ALUNO"));

        aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(UUID.randomUUID())
                .usuarioId(USUARIO).matricula("2026-001").build();
        matematica = Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build();
        fisica = Disciplina.builder().id(11L).nome("Fisica").cargaHoraria(60).build();
        turma = turma(1L, "1A", Set.of(matematica, fisica));
        lenient().when(alunoRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.of(aluno));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Turma turma(Long id, String nome, Set<Disciplina> disciplinas) {
        return Turma.builder().id(id).nome(nome).turno(Turno.MATUTINO).vagas(30)
                .periodoLetivo(PeriodoLetivo.builder().id(5L).nome("2026").build())
                .curso(Curso.builder().id(7L).nome("Ensino Medio").build())
                .disciplinas(new java.util.HashSet<>(disciplinas))
                .build();
    }

    private MatriculaTurma matricula(Turma t, StatusMatricula status) {
        return MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA).aluno(aluno).turma(t).status(status)
                .dataMatricula(LocalDate.of(2026, 2, 1)).build();
    }

    private Avaliacao avaliacao(Disciplina disciplina, String titulo, LocalDate data, String valor) {
        return Avaliacao.builder().id(UUID.randomUUID()).escolaId(ESCOLA).turma(turma).disciplina(disciplina)
                .tipo(TipoAvaliacao.PROVA).titulo(titulo).data(data).valor(new BigDecimal(valor)).build();
    }

    private Nota nota(Avaliacao avaliacao, String valor) {
        return Nota.builder().id(UUID.randomUUID()).escolaId(ESCOLA).avaliacao(avaliacao).aluno(aluno)
                .valor(new BigDecimal(valor)).build();
    }

    private void professorDeMatematica(String nome) {
        Professor professor = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(UUID.randomUUID())
                .build();
        when(alocacaoRepository.findAllComProfessorByEscolaIdAndTurmaIdIn(eq(ESCOLA), anyCollection()))
                .thenReturn(List.of(AlocacaoProfessor.builder().escolaId(ESCOLA).professor(professor).turma(turma)
                        .disciplina(matematica).build()));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA))).thenReturn(List.of(
                Pessoa.builder().id(professor.getPessoaId()).escolaId(ESCOLA).nome(nome).ativo(true).build()));
    }

    @Test
    void boletimSomaOsPontosDeCadaDisciplinaComAsNotasDoAluno() {
        when(matriculaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId()))
                .thenReturn(List.of(matricula(turma, StatusMatricula.ATIVA)));
        professorDeMatematica("Ana Souza");
        Avaliacao prova1 = avaliacao(matematica, "Prova 1", LocalDate.of(2026, 3, 10), "10");
        Avaliacao prova2 = avaliacao(matematica, "Prova 2", LocalDate.of(2026, 5, 10), "15");
        Avaliacao trabalho = avaliacao(matematica, "Trabalho", LocalDate.of(2026, 4, 10), "5");
        when(avaliacaoRepository.findAllByEscolaIdAndTurmaIdIn(eq(ESCOLA), anyCollection()))
                .thenReturn(List.of(prova2, prova1, trabalho));
        when(notaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId()))
                .thenReturn(List.of(nota(prova1, "8.5"), nota(trabalho, "5")));

        List<BoletimTurmaResponse> boletim = service.boletim();

        assertThat(boletim).hasSize(1);
        BoletimTurmaResponse primeira = boletim.get(0);
        assertThat(primeira.turmaNome()).isEqualTo("1A");
        assertThat(primeira.situacaoMatricula()).isEqualTo(StatusMatricula.ATIVA);
        // Grade inteira, por nome: Fisica (sem avaliacao) aparece zerada.
        assertThat(primeira.disciplinas()).extracting(BoletimDisciplina::disciplinaNome)
                .containsExactly("Fisica", "Matematica");
        BoletimDisciplina fis = primeira.disciplinas().get(0);
        assertThat(fis.avaliacoes()).isEmpty();
        assertThat(fis.professorNome()).isNull();
        assertThat(fis.pontosDistribuidos()).isEqualByComparingTo("0");

        BoletimDisciplina mat = primeira.disciplinas().get(1);
        assertThat(mat.professorNome()).isEqualTo("Ana Souza");
        assertThat(mat.avaliacoes()).extracting(AvaliacaoDoAlunoResponse::titulo)
                .containsExactly("Prova 1", "Trabalho", "Prova 2");
        assertThat(mat.avaliacoes().get(2).nota()).isNull();
        assertThat(mat.pontosObtidos()).isEqualByComparingTo("13.5");
        assertThat(mat.pontosAvaliados()).isEqualByComparingTo("15");
        assertThat(mat.pontosDistribuidos()).isEqualByComparingTo("30");
    }

    @Test
    void notaDeAvaliacaoEmRascunhoNaoApareceParaOAluno() {
        when(matriculaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId()))
                .thenReturn(List.of(matricula(turma, StatusMatricula.ATIVA)));
        Avaliacao publicada = avaliacao(matematica, "Prova 1", LocalDate.of(2026, 3, 10), "10");
        Avaliacao rascunho = avaliacao(matematica, "Prova 2", LocalDate.of(2026, 5, 10), "10");
        rascunho.setNotasPublicadas(false);
        when(avaliacaoRepository.findAllByEscolaIdAndTurmaIdIn(ESCOLA, List.of(1L)))
                .thenReturn(List.of(publicada, rascunho));
        when(notaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId()))
                .thenReturn(List.of(nota(publicada, "8"), nota(rascunho, "3")));

        List<AvaliacaoDoAlunoResponse> agenda = service.agenda();

        assertThat(agenda.get(0).nota()).isEqualByComparingTo("8");
        assertThat(agenda.get(1).nota()).isNull();
    }

    @Test
    void boletimTrazFrequenciaPorDisciplinaEGeralComSemAulasNulo() {
        when(matriculaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId()))
                .thenReturn(List.of(matricula(turma, StatusMatricula.ATIVA)));
        // Matematica com 25,1% de faltas; Fisica sem aula registrada (fora do resumo).
        when(frequenciaRepository.resumirPorDisciplina(ESCOLA, aluno.getId(), 1L))
                .thenReturn(List.of(resumo(matematica.getId(), 1000, 251)));

        BoletimTurmaResponse boletim = service.boletim().get(0);

        assertThat(boletim.limiteFaltasPercentual()).isEqualTo(25);
        BoletimDisciplina fis = boletim.disciplinas().get(0);
        assertThat(fis.disciplinaNome()).isEqualTo("Fisica");
        assertThat(fis.frequencia()).isNull();
        BoletimDisciplina mat = boletim.disciplinas().get(1);
        assertThat(mat.frequencia().totalAulas()).isEqualTo(1000);
        assertThat(mat.frequencia().faltas()).isEqualTo(251);
        assertThat(mat.frequencia().percentualFrequencia()).isEqualByComparingTo("74.9");
        assertThat(mat.frequencia().alertaFaltas()).isTrue();
        assertThat(boletim.frequencia().totalAulas()).isEqualTo(1000);
        assertThat(boletim.frequencia().alertaFaltas()).isTrue();
    }

    @Test
    void boletimSemNenhumaAulaNaoTemFrequenciaNemAlerta() {
        when(matriculaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId()))
                .thenReturn(List.of(matricula(turma, StatusMatricula.ATIVA)));
        when(frequenciaRepository.resumirPorDisciplina(ESCOLA, aluno.getId(), 1L)).thenReturn(List.of());

        BoletimTurmaResponse boletim = service.boletim().get(0);

        assertThat(boletim.frequencia()).isNull();
        assertThat(boletim.disciplinas()).extracting(BoletimDisciplina::frequencia).containsOnlyNulls();
    }

    @Test
    void boletimComExatamente25PorCentoDeFaltasNaoAlerta() {
        when(matriculaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId()))
                .thenReturn(List.of(matricula(turma, StatusMatricula.ATIVA)));
        when(frequenciaRepository.resumirPorDisciplina(ESCOLA, aluno.getId(), 1L))
                .thenReturn(List.of(resumo(matematica.getId(), 20, 5), resumo(fisica.getId(), 20, 5)));

        BoletimTurmaResponse boletim = service.boletim().get(0);

        assertThat(boletim.disciplinas()).extracting(d -> d.frequencia().alertaFaltas()).containsOnly(false);
        assertThat(boletim.frequencia().alertaFaltas()).isFalse();
        assertThat(boletim.frequencia().percentualFrequencia()).isEqualByComparingTo("75");
    }

    private static FrequenciaDaDisciplina resumo(Long disciplinaId, long aulas, long faltas) {
        return new FrequenciaDaDisciplina() {
            @Override
            public Long getDisciplinaId() {
                return disciplinaId;
            }

            @Override
            public long getAulas() {
                return aulas;
            }

            @Override
            public long getFaltas() {
                return faltas;
            }
        };
    }

    @Test
    void matriculaCanceladaNaoEntraETrancadaVemDepoisDaAtiva() {
        Turma antiga = turma(2L, "0A", Set.of());
        Turma cancelada = turma(3L, "9Z", Set.of());
        when(matriculaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId())).thenReturn(List.of(
                matricula(antiga, StatusMatricula.TRANCADA), matricula(cancelada, StatusMatricula.CANCELADA),
                matricula(turma, StatusMatricula.ATIVA)));
        professorDeMatematica("Ana Souza");

        List<TurmaDoAlunoResponse> turmas = service.listarMinhasTurmas();

        assertThat(turmas).extracting(TurmaDoAlunoResponse::turmaNome).containsExactly("1A", "0A");
        assertThat(turmas.get(0).disciplinas()).extracting(TurmaDoAlunoResponse.DisciplinaDaTurma::professorNome)
                .containsExactly(null, "Ana Souza");
        assertThat(turmas.get(0).cursoNome()).isEqualTo("Ensino Medio");
    }

    @Test
    void agendaSoTrazAvaliacoesDasTurmasAtivasPorData() {
        Turma trancada = turma(2L, "0A", Set.of());
        when(matriculaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId())).thenReturn(List.of(
                matricula(turma, StatusMatricula.ATIVA), matricula(trancada, StatusMatricula.TRANCADA)));
        Avaliacao depois = avaliacao(fisica, "Lista", LocalDate.of(2026, 6, 1), "2");
        Avaliacao antes = avaliacao(matematica, "Prova 1", LocalDate.of(2026, 3, 10), "10");
        when(avaliacaoRepository.findAllByEscolaIdAndTurmaIdIn(ESCOLA, List.of(1L))).thenReturn(List.of(depois, antes));
        when(notaRepository.findAllByEscolaIdAndAlunoId(ESCOLA, aluno.getId())).thenReturn(List.of(nota(antes, "9")));

        List<AvaliacaoDoAlunoResponse> agenda = service.agenda();

        assertThat(agenda).extracting(AvaliacaoDoAlunoResponse::titulo).containsExactly("Prova 1", "Lista");
        assertThat(agenda.get(0).nota()).isEqualByComparingTo("9");
        assertThat(agenda.get(1).nota()).isNull();
        assertThat(agenda.get(1).disciplinaNome()).isEqualTo("Fisica");
    }

    @Test
    void loginSemCadastroDeAlunoResponde404() {
        when(alunoRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.boletim())
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Aluno nao encontrado");
    }
}
