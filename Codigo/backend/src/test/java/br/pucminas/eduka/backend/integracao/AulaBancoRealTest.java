package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.dto.academico.AtualizarAulaRequest;
import br.pucminas.eduka.backend.dto.academico.AulaRequest;
import br.pucminas.eduka.backend.dto.academico.AulaResponse;
import br.pucminas.eduka.backend.dto.academico.ChamadaItem;
import br.pucminas.eduka.backend.dto.academico.TurmaRequest;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.comum.RegistroEmUsoException;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository;
import br.pucminas.eduka.backend.service.academico.AcademicoSecretariaService;
import br.pucminas.eduka.backend.service.academico.AulaService;

/**
 * Aulas e chamada no banco: o fluxo do professor, as constraints da V35, o
 * isolamento entre escolas e a soma de faltas por aluno e disciplina que o
 * boletim usa.
 */
class AulaBancoRealTest extends BancoRealTest {

    @Autowired
    private AulaService aulaService;
    @Autowired
    private FrequenciaRepository frequenciaRepository;
    @Autowired
    private AcademicoSecretariaService academicoService;

    private UUID escola;
    private long turma;
    private long matematica;
    private long fisica;
    private UUID professorUsuario;
    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void turmaComProfessorEDoisAlunos() {
        escola = dados.escola();
        turma = dados.turma(escola, "1A", 30);
        matematica = disciplinaNaGrade("Matematica");
        fisica = disciplinaNaGrade("Fisica");
        professorUsuario = dados.usuario(escola, "PROFESSOR", "Paulo Lima");
        UUID professor = jdbc.queryForObject("INSERT INTO PROFESSOR (ESCOLA_ID, PESSOA_ID, USUARIO_ID) "
                + "SELECT ESCOLA_ID, PESSOA_ID, ID_USUARIO FROM USUARIO WHERE ID_USUARIO = ? RETURNING ID_PROFESSOR",
                UUID.class, professorUsuario);
        alocar(professor, matematica);
        alocar(professor, fisica);
        ana = dados.aluno(escola, "Ana Souza");
        bruno = dados.aluno(escola, "Bruno Alves");
        dados.matricula(escola, ana, turma, StatusMatricula.ATIVA, LocalDate.now().minusDays(30));
        dados.matricula(escola, bruno, turma, StatusMatricula.ATIVA, LocalDate.now().minusDays(30));
        autenticarComo(professorUsuario, "PROFESSOR", escola);
    }

    private long disciplinaNaGrade(String nome) {
        Long id = jdbc.queryForObject("INSERT INTO disciplina (escola_id, nome, carga_horaria) VALUES (?, ?, 60) "
                + "RETURNING id", Long.class, escola, nome + " " + DadosDeTeste.unico());
        jdbc.update("INSERT INTO turma_disciplina (turma_id, disciplina_id) VALUES (?, ?)", turma, id);
        return id;
    }

    private void alocar(UUID professor, long disciplina) {
        jdbc.update("INSERT INTO ALOCACAO_PROFESSOR (ESCOLA_ID, PROFESSOR_ID, TURMA_ID, DISCIPLINA_ID) "
                + "VALUES (?, ?, ?, ?)", escola, professor, turma, disciplina);
    }

    private AulaResponse registrar(long disciplina, LocalDate data, boolean anaPresente, boolean brunoPresente) {
        return aulaService.registrar(new AulaRequest(turma, disciplina, data, "Conteudo",
                List.of(new ChamadaItem(ana, anaPresente), new ChamadaItem(bruno, brunoPresente))));
    }

    @Test
    void registraBuscaECorrigeAChamada() {
        AulaResponse registrada = registrar(matematica, LocalDate.now().minusDays(1), true, false);

        assertThat(registrada.id()).isNotNull();
        assertThat(registrada.criadoEm()).isNotNull();
        assertThat(registrada.presentes()).isEqualTo(1);
        assertThat(registrada.faltas()).isEqualTo(1);
        assertThat(registrada.chamada()).extracting("alunoNome", "presente")
                .containsExactly(tuple("Ana Souza", true),
                        tuple("Bruno Alves", false));

        AulaResponse corrigida = aulaService.atualizar(registrada.id(), new AtualizarAulaRequest("Novo conteudo",
                List.of(new ChamadaItem(ana, true), new ChamadaItem(bruno, true))));

        assertThat(corrigida.conteudo()).isEqualTo("Novo conteudo");
        assertThat(corrigida.faltas()).isZero();
        assertThat(corrigida.atualizadoEm()).isNotNull();
        assertThat(aulaService.buscar(registrada.id()).chamada()).extracting("presente").containsOnly(true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FREQUENCIA WHERE AULA_ID = ?", Integer.class,
                registrada.id())).isEqualTo(2);
    }

    @Test
    void listaEmPaginasDasMaisRecentesComOsTotais() {
        registrar(matematica, LocalDate.now().minusDays(3), true, true);
        AulaResponse maisRecente = registrar(matematica, LocalDate.now().minusDays(1), false, false);
        registrar(fisica, LocalDate.now().minusDays(1), true, true);

        var primeira = aulaService.listar(turma, matematica, new Paginacao(0, 1), null);
        var segunda = aulaService.listar(turma, matematica, new Paginacao(1, 1), null);

        assertThat(primeira.totalElementos()).isEqualTo(2);
        assertThat(primeira.totalPaginas()).isEqualTo(2);
        assertThat(primeira.conteudo()).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(maisRecente.id());
            assertThat(a.presentes()).isZero();
            assertThat(a.faltas()).isEqualTo(2);
            assertThat(a.turmaNome()).isEqualTo("1A");
        });
        assertThat(segunda.conteudo()).singleElement().satisfies(a -> assertThat(a.presentes()).isEqualTo(2));
    }

    @Test
    void outraEscolaNaoEnxergaAAula() {
        AulaResponse registrada = registrar(matematica, LocalDate.now(), true, true);

        UUID outraEscola = dados.escola();
        autenticarComo(dados.usuario(outraEscola, "PROFESSOR", "Outro Professor"), "PROFESSOR", outraEscola);

        assertThatThrownBy(() -> aulaService.buscar(registrada.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void professorSemAlocacaoNaDisciplinaNaoMexeNaAula() {
        AulaResponse registrada = registrar(matematica, LocalDate.now(), true, true);

        UUID outroUsuario = dados.usuario(escola, "PROFESSOR", "Outra Professora");
        jdbc.update("INSERT INTO PROFESSOR (ESCOLA_ID, PESSOA_ID, USUARIO_ID) "
                + "SELECT ESCOLA_ID, PESSOA_ID, ID_USUARIO FROM USUARIO WHERE ID_USUARIO = ?", outroUsuario);
        autenticarComo(outroUsuario, "PROFESSOR", escola);

        assertThatThrownBy(() -> aulaService.buscar(registrada.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> aulaService.listar(turma, matematica, new Paginacao(0, 20), null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void aulasSobrevivemADesalocarOProfessor() {
        AulaResponse registrada = registrar(matematica, LocalDate.now(), true, false);

        jdbc.update("DELETE FROM ALOCACAO_PROFESSOR WHERE TURMA_ID = ? AND DISCIPLINA_ID = ?", turma, matematica);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AULA WHERE ID_AULA = ?", Integer.class, registrada.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FREQUENCIA WHERE AULA_ID = ?", Integer.class,
                registrada.id())).isEqualTo(2);
    }

    @Test
    void aulaAnteriorAMatriculaNaoTemChamadaDoAlunoNovo() {
        UUID carla = dados.aluno(escola, "Carla Dias");
        dados.matricula(escola, carla, turma, StatusMatricula.ATIVA, LocalDate.now().minusDays(5));

        AulaResponse antiga = registrar(matematica, LocalDate.now().minusDays(10), true, true);

        assertThat(antiga.chamada()).extracting("alunoNome").containsExactly("Ana Souza", "Bruno Alves");
        assertThatThrownBy(() -> registrar(matematica, LocalDate.now(), true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chamada incompleta");
        assertThatThrownBy(() -> aulaService.registrar(new AulaRequest(turma, matematica,
                LocalDate.now().minusDays(10), "Conteudo", List.of(new ChamadaItem(ana, true),
                        new ChamadaItem(bruno, true), new ChamadaItem(carla, false)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aluno sem matricula ativa na turma da aula");
    }

    @Test
    void bancoBarraFrequenciaRepetidaDoAlunoNaAula() {
        AulaResponse registrada = registrar(matematica, LocalDate.now(), true, true);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO FREQUENCIA (ESCOLA_ID, AULA_ID, ALUNO_ID, PRESENTE) "
                + "VALUES (?, ?, ?, false)", escola, registrada.id(), ana))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void excluirApagaAAulaEAChamadaSemMexerNasOutrasEOBoletimDeixaDeContar() {
        AulaResponse errada = registrar(matematica, LocalDate.now().minusDays(2), false, false);
        AulaResponse certa = registrar(matematica, LocalDate.now().minusDays(1), true, true);

        aulaService.excluir(errada.id());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AULA WHERE ID_AULA = ?", Integer.class, errada.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FREQUENCIA WHERE AULA_ID = ?", Integer.class,
                errada.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FREQUENCIA WHERE AULA_ID = ?", Integer.class,
                certa.id())).isEqualTo(2);
        assertThat(frequenciaRepository.resumirPorDisciplina(escola, ana, turma))
                .extracting(f -> f.getAulas(), f -> f.getFaltas())
                .containsExactly(tuple(1L, 0L));
        assertThatThrownBy(() -> aulaService.buscar(errada.id())).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void outraEscolaNaoExcluiAAula() {
        AulaResponse registrada = registrar(matematica, LocalDate.now(), true, true);

        UUID outraEscola = dados.escola();
        autenticarComo(dados.usuario(outraEscola, "PROFESSOR", "Outro Professor"), "PROFESSOR", outraEscola);

        assertThatThrownBy(() -> aulaService.excluir(registrada.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AULA WHERE ID_AULA = ?", Integer.class, registrada.id()))
                .isEqualTo(1);
    }

    @Test
    void secretariaNaoTiraDaGradeNemExcluiTurmaComAula() {
        registrar(matematica, LocalDate.now().minusDays(1), true, true);
        Long periodo = jdbc.queryForObject("SELECT periodo_letivo_id FROM turma WHERE id = ?", Long.class, turma);
        Long curso = jdbc.queryForObject("SELECT curso_id FROM turma WHERE id = ?", Long.class, turma);
        TurmaRequest semMatematica = new TurmaRequest();
        semMatematica.setPeriodoLetivoId(periodo);
        semMatematica.setCursoId(curso);
        semMatematica.setNome("1A");
        semMatematica.setVagas(30);
        semMatematica.setTurno(Turno.MATUTINO);
        semMatematica.setDisciplinaIds(Set.of(fisica));

        assertThatThrownBy(() -> academicoService.atualizarTurma(escola, turma, semMatematica))
                .isInstanceOf(RegistroEmUsoException.class)
                .hasMessageContaining("tem aulas registradas nesta turma");

        jdbc.update("DELETE FROM MATRICULA_TURMA WHERE TURMA_ID = ?", turma);
        jdbc.update("DELETE FROM ALOCACAO_PROFESSOR WHERE TURMA_ID = ?", turma);
        assertThatThrownBy(() -> academicoService.excluirTurma(escola, turma))
                .isInstanceOf(RegistroEmUsoException.class)
                .hasMessage("A turma tem aulas registradas e nao pode ser excluida");
    }

    @Test
    void resumeAulasEFaltasDoAlunoPorDisciplina() {
        registrar(matematica, LocalDate.now().minusDays(3), true, false);
        registrar(matematica, LocalDate.now().minusDays(2), false, false);
        registrar(matematica, LocalDate.now().minusDays(1), true, true);
        registrar(fisica, LocalDate.now().minusDays(1), false, true);

        var deAna = frequenciaRepository.resumirPorDisciplina(escola, ana, turma);
        var deBruno = frequenciaRepository.resumirPorDisciplina(escola, bruno, turma);

        assertThat(deAna).extracting(f -> f.getDisciplinaId(), f -> f.getAulas(), f -> f.getFaltas())
                .containsExactlyInAnyOrder(tuple(matematica, 3L, 1L),
                        tuple(fisica, 1L, 1L));
        assertThat(deBruno).extracting(f -> f.getDisciplinaId(), f -> f.getAulas(), f -> f.getFaltas())
                .containsExactlyInAnyOrder(tuple(matematica, 3L, 2L),
                        tuple(fisica, 1L, 0L));
        assertThat(frequenciaRepository.resumirPorDisciplina(escola, ana, turma + 1000)).isEmpty();
    }
}
