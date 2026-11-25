package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
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
        gradeDeSegundaASabado(alocar(professor, matematica), "07:30", "08:20");
        gradeDeSegundaASabado(alocar(professor, fisica), "08:20", "09:10");
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

    private UUID alocar(UUID professor, long disciplina) {
        return jdbc.queryForObject("INSERT INTO ALOCACAO_PROFESSOR (ESCOLA_ID, PROFESSOR_ID, TURMA_ID, DISCIPLINA_ID) "
                + "VALUES (?, ?, ?, ?) RETURNING ID_ALOCACAO", UUID.class, escola, professor, turma, disciplina);
    }

    /** Aula da disciplina todo dia da grade: so domingo fica sem aula. */
    private void gradeDeSegundaASabado(UUID alocacao, String inicio, String fim) {
        for (DiaSemana dia : DiaSemana.values()) {
            jdbc.update("INSERT INTO HORARIO_TURMA (ESCOLA_ID, ALOCACAO_ID, DIA_SEMANA, HORA_INICIO, HORA_FIM) "
                    + "VALUES (?, ?, ?, ?::time, ?::time)", escola, alocacao, dia.name(), inicio, fim);
        }
    }

    /** O dia de aula {@code dias} dias letivos antes de hoje (0 = hoje ou, num domingo, o sabado). */
    private static LocalDate letivo(int dias) {
        LocalDate dia = LocalDate.now();
        int contados = -1;
        while (true) {
            if (dia.getDayOfWeek() != DayOfWeek.SUNDAY && ++contados == dias) {
                return dia;
            }
            dia = dia.minusDays(1);
        }
    }

    private AulaResponse registrar(long disciplina, LocalDate data, boolean anaPresente, boolean brunoPresente) {
        return aulaService.registrar(new AulaRequest(turma, disciplina, data, "Conteudo",
                List.of(new ChamadaItem(ana, anaPresente), new ChamadaItem(bruno, brunoPresente))));
    }

    @Test
    void registraBuscaECorrigeAChamada() {
        AulaResponse registrada = registrar(matematica, letivo(1), true, false);

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
        registrar(matematica, letivo(3), true, true);
        AulaResponse maisRecente = registrar(matematica, letivo(1), false, false);
        registrar(fisica, letivo(1), true, true);

        var primeira = aulaService.listar(turma, matematica, null, null, null, new Paginacao(0, 1), null);
        var segunda = aulaService.listar(turma, matematica, null, null, null, new Paginacao(1, 1), null);

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
    void filtraOHistoricoPelasDatasInclusive() {
        registrar(matematica, letivo(10), true, true);
        AulaResponse dentro = registrar(matematica, letivo(5), true, true);
        AulaResponse naPonta = registrar(matematica, letivo(3), true, true);
        AulaResponse recente = registrar(matematica, letivo(1), true, true);

        var periodo = aulaService.listar(turma, matematica, letivo(5), letivo(3), null, new Paginacao(0, 20), null);
        var desde = aulaService.listar(turma, matematica, letivo(3), null, null, new Paginacao(0, 20), null);

        assertThat(periodo.conteudo()).extracting("id").containsExactly(naPonta.id(), dentro.id());
        assertThat(desde.conteudo()).extracting("id").containsExactly(recente.id(), naPonta.id());
    }

    @Test
    void buscaPeloConteudoEContaOsMesesComOsTotaisDaChamada() {
        AulaResponse logaritmo = aulaService.registrar(new AulaRequest(turma, matematica, letivo(2),
                "Introducao a LOGARITMOS", List.of(new ChamadaItem(ana, true), new ChamadaItem(bruno, false))));
        registrar(matematica, letivo(1), true, true);
        registrar(fisica, letivo(1), true, true);

        var achadas = aulaService.listar(turma, matematica, null, null, "logaritmo", new Paginacao(0, 20), null);
        var meses = aulaService.meses(turma, matematica, null, null, null);
        var mesesDaBusca = aulaService.meses(turma, matematica, null, null, "logar");

        assertThat(achadas.conteudo()).extracting("id").containsExactly(logaritmo.id());
        // As duas aulas de Matematica (a de Fisica fica de fora), em um ou dois meses conforme o dia de hoje.
        assertThat(meses).hasSizeBetween(1, 2);
        assertThat(meses.stream().mapToLong(m -> m.aulas()).sum()).isEqualTo(2);
        assertThat(meses.stream().mapToLong(m -> m.presentes()).sum()).isEqualTo(3);
        assertThat(meses.stream().mapToLong(m -> m.faltas()).sum()).isEqualTo(1);
        assertThat(mesesDaBusca).singleElement().satisfies(m -> {
            assertThat(m.mes()).isEqualTo(letivo(2).toString().substring(0, 7));
            assertThat(m.aulas()).isEqualTo(1);
            assertThat(m.faltas()).isEqualTo(1);
        });
    }

    @Test
    void naoRegistraAulaEmDiaSemAulaDaDisciplinaNaGrade() {
        LocalDate dia = letivo(1);
        jdbc.update("DELETE FROM HORARIO_TURMA WHERE ALOCACAO_ID IN (SELECT ID_ALOCACAO FROM ALOCACAO_PROFESSOR "
                + "WHERE TURMA_ID = ? AND DISCIPLINA_ID = ?) AND DIA_SEMANA = ?", turma, fisica,
                DiaSemana.de(dia).orElseThrow().name());

        assertThatThrownBy(() -> registrar(fisica, dia, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nao ha aula");
        // A outra disciplina, com aula nesse dia, registra normalmente, e o dia de aula vem na chamada.
        assertThat(registrar(matematica, dia, true, true).data()).isEqualTo(dia);
        assertThat(aulaService.chamadaDoDia(turma, fisica, dia).diaDeAula()).isFalse();
        assertThat(aulaService.chamadaDoDia(turma, matematica, dia).diaDeAula()).isTrue();
    }

    @Test
    void outraEscolaNaoEnxergaAAula() {
        AulaResponse registrada = registrar(matematica, letivo(0), true, true);

        UUID outraEscola = dados.escola();
        autenticarComo(dados.usuario(outraEscola, "PROFESSOR", "Outro Professor"), "PROFESSOR", outraEscola);

        assertThatThrownBy(() -> aulaService.buscar(registrada.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void professorSemAlocacaoNaDisciplinaNaoMexeNaAula() {
        AulaResponse registrada = registrar(matematica, letivo(0), true, true);

        UUID outroUsuario = dados.usuario(escola, "PROFESSOR", "Outra Professora");
        jdbc.update("INSERT INTO PROFESSOR (ESCOLA_ID, PESSOA_ID, USUARIO_ID) "
                + "SELECT ESCOLA_ID, PESSOA_ID, ID_USUARIO FROM USUARIO WHERE ID_USUARIO = ?", outroUsuario);
        autenticarComo(outroUsuario, "PROFESSOR", escola);

        assertThatThrownBy(() -> aulaService.buscar(registrada.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> aulaService.listar(turma, matematica, null, null, null, new Paginacao(0, 20), null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void aulasSobrevivemADesalocarOProfessor() {
        AulaResponse registrada = registrar(matematica, letivo(0), true, false);

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

        AulaResponse antiga = registrar(matematica, letivo(10), true, true);

        assertThat(antiga.chamada()).extracting("alunoNome").containsExactly("Ana Souza", "Bruno Alves");
        assertThatThrownBy(() -> registrar(matematica, letivo(0), true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chamada incompleta");
        assertThatThrownBy(() -> aulaService.registrar(new AulaRequest(turma, matematica,
                letivo(10), "Conteudo", List.of(new ChamadaItem(ana, true),
                        new ChamadaItem(bruno, true), new ChamadaItem(carla, false)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aluno sem matricula ativa na turma da aula");
    }

    @Test
    void bancoBarraFrequenciaRepetidaDoAlunoNaAula() {
        AulaResponse registrada = registrar(matematica, letivo(0), true, true);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO FREQUENCIA (ESCOLA_ID, AULA_ID, ALUNO_ID, PRESENTE) "
                + "VALUES (?, ?, ?, false)", escola, registrada.id(), ana))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void excluirApagaAAulaEAChamadaSemMexerNasOutrasEOBoletimDeixaDeContar() {
        AulaResponse errada = registrar(matematica, letivo(2), false, false);
        AulaResponse certa = registrar(matematica, letivo(1), true, true);

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
        AulaResponse registrada = registrar(matematica, letivo(0), true, true);

        UUID outraEscola = dados.escola();
        autenticarComo(dados.usuario(outraEscola, "PROFESSOR", "Outro Professor"), "PROFESSOR", outraEscola);

        assertThatThrownBy(() -> aulaService.excluir(registrada.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AULA WHERE ID_AULA = ?", Integer.class, registrada.id()))
                .isEqualTo(1);
    }

    @Test
    void secretariaNaoTiraDaGradeNemExcluiTurmaComAula() {
        registrar(matematica, letivo(1), true, true);
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
        registrar(matematica, letivo(3), true, false);
        registrar(matematica, letivo(2), false, false);
        registrar(matematica, letivo(1), true, true);
        registrar(fisica, letivo(1), false, true);

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
