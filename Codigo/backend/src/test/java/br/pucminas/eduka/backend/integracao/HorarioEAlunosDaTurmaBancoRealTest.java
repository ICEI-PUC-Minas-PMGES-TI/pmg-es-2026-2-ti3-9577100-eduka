package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.DiaSemana;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse;
import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse.HorarioDaDisciplina;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.service.PortalProfessorService;

/**
 * Portal do professor (IU13): o horario de cada disciplina vem da grade da
 * turma (HORARIO_TURMA) e o Ver alunos so abre turma em que ele leciona.
 */
class HorarioEAlunosDaTurmaBancoRealTest extends BancoRealTest {

    private static final LocalTime SETE_E_MEIA = LocalTime.of(7, 30);
    private static final LocalTime OITO_E_VINTE = LocalTime.of(8, 20);

    @Autowired
    private PortalProfessorService portalProfessorService;

    private UUID escola;
    private long turma;
    private UUID alocacao;
    private UUID usuarioProfessor;

    @BeforeEach
    void turmaComProfessorAlocado() {
        escola = dados.escola();
        turma = dados.turma(escola, "2A", 30);
        Long matematica = jdbc.queryForObject("INSERT INTO disciplina (escola_id, nome, carga_horaria) "
                + "VALUES (?, 'Matematica', 60) RETURNING id", Long.class, escola);
        jdbc.update("INSERT INTO turma_disciplina (turma_id, disciplina_id) VALUES (?, ?)", turma, matematica);
        // O portal acha o professor pelo login (PROFESSOR.USUARIO_ID).
        usuarioProfessor = dados.usuario(escola, "PROFESSOR", "Joao Martins");
        UUID pessoa = jdbc.queryForObject("SELECT PESSOA_ID FROM USUARIO WHERE ID_USUARIO = ?", UUID.class,
                usuarioProfessor);
        UUID professor = jdbc.queryForObject("INSERT INTO PROFESSOR (ESCOLA_ID, PESSOA_ID, USUARIO_ID) "
                + "VALUES (?, ?, ?) RETURNING ID_PROFESSOR", UUID.class, escola, pessoa, usuarioProfessor);
        alocacao = jdbc.queryForObject("INSERT INTO ALOCACAO_PROFESSOR (ESCOLA_ID, PROFESSOR_ID, TURMA_ID, "
                + "DISCIPLINA_ID) VALUES (?, ?, ?, ?) RETURNING ID_ALOCACAO", UUID.class, escola, professor, turma,
                matematica);
        autenticarComo(usuarioProfessor, "PROFESSOR", escola);
    }

    private void aula(DiaSemana dia) {
        jdbc.update("INSERT INTO HORARIO_TURMA (ESCOLA_ID, ALOCACAO_ID, DIA_SEMANA, HORA_INICIO, HORA_FIM) "
                + "VALUES (?, ?, ?, ?, ?)", escola, alocacao, dia.name(), SETE_E_MEIA, OITO_E_VINTE);
    }

    @Test
    void professorVeOHorarioDaGradeEmOrdemDeDia() {
        aula(DiaSemana.SEXTA);
        aula(DiaSemana.SEGUNDA);

        TurmaDoProfessorResponse minha = portalProfessorService.listarMinhasTurmas().get(0);

        assertThat(minha.disciplinas()).singleElement().satisfies(d -> {
            assertThat(d.horarios()).extracting(HorarioDaDisciplina::diaSemana)
                    .containsExactly(DiaSemana.SEGUNDA, DiaSemana.SEXTA);
            assertThat(d.horarios().get(0).horaInicio()).isEqualTo(SETE_E_MEIA);
            assertThat(d.horarios().get(0).horaFim()).isEqualTo(OITO_E_VINTE);
        });
    }

    @Test
    void semGradeMontadaOHorarioVemVazio() {
        assertThat(portalProfessorService.listarMinhasTurmas().get(0).disciplinas().get(0).horarios()).isEmpty();
    }

    @Test
    void professorVeOsAlunosSoDasTurmasDele() {
        UUID ana = dados.aluno(escola, "Ana Lima");
        UUID bruno = dados.aluno(escola, "Bruno Costa");
        UUID carla = dados.aluno(escola, "Carla Dias");
        dados.matricula(escola, bruno, turma, StatusMatricula.ATIVA, LocalDate.now());
        dados.matricula(escola, ana, turma, StatusMatricula.TRANCADA, LocalDate.now());
        dados.matricula(escola, carla, turma, StatusMatricula.CANCELADA, LocalDate.now());
        long outraTurma = dados.turma(escola, "3B", 30);

        assertThat(portalProfessorService.alunosDaTurma(turma)).extracting(a -> a.nome())
                .containsExactly("Ana Lima", "Bruno Costa");
        assertThatThrownBy(() -> portalProfessorService.alunosDaTurma(outraTurma))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
