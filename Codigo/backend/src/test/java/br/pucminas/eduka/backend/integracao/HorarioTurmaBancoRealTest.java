package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.dto.academico.AtualizarHorarioTurmaRequest;
import br.pucminas.eduka.backend.dto.academico.HorarioTurmaRequest;
import br.pucminas.eduka.backend.dto.academico.HorarioTurmaResponse;
import br.pucminas.eduka.backend.exception.academico.HorarioConflitanteException;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.service.academico.HorarioTurmaService;

/**
 * Grade semanal no banco: migration V36, mapeamento da entidade, as consultas
 * de sobreposicao por turma e por professor, a constraint de horas, o
 * cascade da alocacao e o isolamento entre escolas.
 */
class HorarioTurmaBancoRealTest extends BancoRealTest {

    @Autowired
    private HorarioTurmaService horarioService;

    private UUID escola;
    private long turma1A;
    private long turma2B;
    private UUID alocacaoMatematica1A;
    private UUID alocacaoHistoria1A;
    private UUID alocacaoMatematica2B;

    @BeforeEach
    void gradeDaEscola() {
        escola = dados.escola();
        turma1A = dados.turma(escola, "1A", 30);
        turma2B = dados.turma(escola, "2B", 30);
        UUID ana = dados.professor(escola, "Ana", LocalDate.of(2020, 1, 1), true);
        UUID bruno = dados.professor(escola, "Bruno", LocalDate.of(2020, 1, 1), true);
        long matematica = disciplina("Matematica");
        long historia = disciplina("Historia");
        alocacaoMatematica1A = alocar(ana, turma1A, matematica);
        alocacaoHistoria1A = alocar(bruno, turma1A, historia);
        alocacaoMatematica2B = alocar(ana, turma2B, matematica);
        autenticarComo("SECRETARIA", escola);
    }

    private long disciplina(String nome) {
        return jdbc.queryForObject("INSERT INTO disciplina (escola_id, nome, carga_horaria) VALUES (?, ?, 80) "
                + "RETURNING id", Long.class, escola, nome);
    }

    private UUID alocar(UUID professor, long turma, long disciplina) {
        jdbc.update("INSERT INTO turma_disciplina (turma_id, disciplina_id) VALUES (?, ?)", turma, disciplina);
        return jdbc.queryForObject("INSERT INTO ALOCACAO_PROFESSOR (ESCOLA_ID, PROFESSOR_ID, TURMA_ID, DISCIPLINA_ID) "
                + "VALUES (?, ?, ?, ?) RETURNING ID_ALOCACAO", UUID.class, escola, professor, turma, disciplina);
    }

    private HorarioTurmaResponse criar(long turma, UUID alocacao, DiaSemana dia, int inicio, int fim) {
        return horarioService.criar(turma, new HorarioTurmaRequest(alocacao, dia, LocalTime.of(inicio, 0),
                LocalTime.of(fim, 0)));
    }

    private int aulasGravadas() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM HORARIO_TURMA WHERE ESCOLA_ID = ?", Integer.class, escola);
    }

    @Test
    void gradeDaTurmaVemOrdenadaPorDiaEHoraComDisciplinaEProfessor() {
        criar(turma1A, alocacaoHistoria1A, DiaSemana.QUARTA, 8, 9);
        criar(turma1A, alocacaoMatematica1A, DiaSemana.SEGUNDA, 10, 11);
        criar(turma1A, alocacaoHistoria1A, DiaSemana.SEGUNDA, 8, 9);
        criar(turma2B, alocacaoMatematica2B, DiaSemana.TERCA, 8, 9);

        List<HorarioTurmaResponse> grade = horarioService.listar(turma1A);

        assertThat(grade).extracting(HorarioTurmaResponse::diaSemana, h -> h.horaInicio().getHour(),
                HorarioTurmaResponse::disciplinaNome, HorarioTurmaResponse::professorNome)
                .containsExactly(
                        tuple(DiaSemana.SEGUNDA, 8, "Historia", "Bruno"),
                        tuple(DiaSemana.SEGUNDA, 10, "Matematica", "Ana"),
                        tuple(DiaSemana.QUARTA, 8, "Historia", "Bruno"));
    }

    @Test
    void sobreposicaoNaTurmaEBarradaEBordaQueEncostaPassa() {
        criar(turma1A, alocacaoMatematica1A, DiaSemana.SEGUNDA, 8, 10);

        assertThatThrownBy(() -> criar(turma1A, alocacaoHistoria1A, DiaSemana.SEGUNDA, 9, 11))
                .isInstanceOfSatisfying(HorarioConflitanteException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("HORARIO_TURMA_CONFLITANTE"));
        assertThat(aulasGravadas()).isEqualTo(1);

        criar(turma1A, alocacaoHistoria1A, DiaSemana.SEGUNDA, 10, 11);
        criar(turma1A, alocacaoHistoria1A, DiaSemana.SEGUNDA, 7, 8);
        criar(turma1A, alocacaoHistoria1A, DiaSemana.TERCA, 8, 10);
        assertThat(aulasGravadas()).isEqualTo(4);
    }

    @Test
    void professorNaoTemDuasAulasAoMesmoTempoEmTurmasDiferentes() {
        criar(turma1A, alocacaoMatematica1A, DiaSemana.SEGUNDA, 8, 10);

        assertThatThrownBy(() -> criar(turma2B, alocacaoMatematica2B, DiaSemana.SEGUNDA, 9, 11))
                .isInstanceOfSatisfying(HorarioConflitanteException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("HORARIO_PROFESSOR_CONFLITANTE"));
        assertThat(aulasGravadas()).isEqualTo(1);

        criar(turma2B, alocacaoMatematica2B, DiaSemana.SEGUNDA, 10, 11);
        assertThat(aulasGravadas()).isEqualTo(2);
    }

    @Test
    void atualizaDiaEHorarioSemConflitarConsigoMesma() {
        HorarioTurmaResponse aula = criar(turma1A, alocacaoMatematica1A, DiaSemana.SEGUNDA, 8, 9);
        criar(turma1A, alocacaoHistoria1A, DiaSemana.SEGUNDA, 9, 10);

        HorarioTurmaResponse movida = horarioService.atualizar(turma1A, aula.id(), new AtualizarHorarioTurmaRequest(
                DiaSemana.SEGUNDA, LocalTime.of(7, 30), LocalTime.of(9, 0)));
        assertThat(movida.horaInicio()).isEqualTo(LocalTime.of(7, 30));

        assertThatThrownBy(() -> horarioService.atualizar(turma1A, aula.id(), new AtualizarHorarioTurmaRequest(
                DiaSemana.SEGUNDA, LocalTime.of(8, 30), LocalTime.of(9, 30))))
                .isInstanceOf(HorarioConflitanteException.class);

        HorarioTurmaResponse paraSexta = horarioService.atualizar(turma1A, aula.id(),
                new AtualizarHorarioTurmaRequest(DiaSemana.SEXTA, LocalTime.of(8, 0), LocalTime.of(9, 0)));
        assertThat(paraSexta.diaSemana()).isEqualTo(DiaSemana.SEXTA);
        assertThat(jdbc.queryForObject("SELECT DIA_SEMANA FROM HORARIO_TURMA WHERE ID_HORARIO_TURMA = ?",
                String.class, aula.id())).isEqualTo("SEXTA");
    }

    @Test
    void removeAulaDaGrade() {
        HorarioTurmaResponse aula = criar(turma1A, alocacaoMatematica1A, DiaSemana.SEGUNDA, 8, 9);

        horarioService.remover(turma1A, aula.id());

        assertThat(aulasGravadas()).isZero();
    }

    @Test
    void alocacaoDeOutraTurmaEhRecusada() {
        assertThatThrownBy(() -> criar(turma1A, alocacaoMatematica2B, DiaSemana.SEGUNDA, 8, 9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(aulasGravadas()).isZero();
    }

    @Test
    void desalocarOProfessorLevaAsAulasDaGrade() {
        criar(turma1A, alocacaoMatematica1A, DiaSemana.SEGUNDA, 8, 9);
        criar(turma1A, alocacaoMatematica1A, DiaSemana.QUARTA, 8, 9);
        criar(turma1A, alocacaoHistoria1A, DiaSemana.TERCA, 8, 9);

        jdbc.update("DELETE FROM ALOCACAO_PROFESSOR WHERE ID_ALOCACAO = ?", alocacaoMatematica1A);

        assertThat(horarioService.listar(turma1A)).extracting(HorarioTurmaResponse::disciplinaNome)
                .containsExactly("Historia");
    }

    @Test
    void bancoRecusaIntervaloComInicioNaoAnteriorAoFimEDiaForaDaSemana() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO HORARIO_TURMA (ESCOLA_ID, ALOCACAO_ID, DIA_SEMANA, "
                + "HORA_INICIO, HORA_FIM) VALUES (?, ?, 'SEGUNDA', '09:00', '09:00')", escola,
                alocacaoMatematica1A)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO HORARIO_TURMA (ESCOLA_ID, ALOCACAO_ID, DIA_SEMANA, "
                + "HORA_INICIO, HORA_FIM) VALUES (?, ?, 'DOMINGO', '08:00', '09:00')", escola,
                alocacaoMatematica1A)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outraEscolaNaoVeNemMontaAGradeDaTurma() {
        HorarioTurmaResponse aula = criar(turma1A, alocacaoMatematica1A, DiaSemana.SEGUNDA, 8, 9);
        autenticarComo("SECRETARIA", dados.escola());

        assertThatThrownBy(() -> horarioService.listar(turma1A)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> criar(turma1A, alocacaoHistoria1A, DiaSemana.TERCA, 8, 9))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> horarioService.remover(turma1A, aula.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(aulasGravadas()).isEqualTo(1);
    }
}
