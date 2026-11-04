package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.DefinirHorariosRequest;
import br.pucminas.eduka.backend.dto.DefinirHorariosRequest.Horario;
import br.pucminas.eduka.backend.dto.HorarioAulaResponse;
import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.service.HorarioAulaService;
import br.pucminas.eduka.backend.service.PortalProfessorService;

/**
 * Horario das aulas e Ver alunos (IU13): a secretaria define o horario da
 * disciplina na turma, o professor ve o horario e os alunos so das turmas
 * dele.
 */
class HorarioEAlunosDaTurmaBancoRealTest extends BancoRealTest {

    private static final LocalTime SETE_E_MEIA = LocalTime.of(7, 30);

    @Autowired
    private HorarioAulaService horarioService;
    @Autowired
    private PortalProfessorService portalProfessorService;

    private UUID escola;
    private long turma;
    private long matematica;
    private long fisica;
    private UUID usuarioProfessor;

    @BeforeEach
    void turmaComProfessorAlocado() {
        escola = dados.escola();
        turma = dados.turma(escola, "2A", 30);
        matematica = disciplinaNaGrade("Matematica");
        fisica = disciplinaNaGrade("Fisica");
        // O portal acha o professor pelo login (PROFESSOR.USUARIO_ID).
        usuarioProfessor = dados.usuario(escola, "PROFESSOR", "Joao Martins");
        UUID pessoa = jdbc.queryForObject("SELECT PESSOA_ID FROM USUARIO WHERE ID_USUARIO = ?", UUID.class,
                usuarioProfessor);
        UUID professor = jdbc.queryForObject("INSERT INTO PROFESSOR (ESCOLA_ID, PESSOA_ID, USUARIO_ID) "
                + "VALUES (?, ?, ?) RETURNING ID_PROFESSOR", UUID.class, escola, pessoa, usuarioProfessor);
        jdbc.update("INSERT INTO ALOCACAO_PROFESSOR (ESCOLA_ID, PROFESSOR_ID, TURMA_ID, DISCIPLINA_ID) "
                + "VALUES (?, ?, ?, ?)", escola, professor, turma, matematica);
        autenticarComo("SECRETARIA", escola);
    }

    private long disciplinaNaGrade(String nome) {
        Long id = jdbc.queryForObject("INSERT INTO disciplina (escola_id, nome, carga_horaria) VALUES (?, ?, 60) "
                + "RETURNING id", Long.class, escola, nome + " " + UUID.randomUUID());
        jdbc.update("INSERT INTO turma_disciplina (turma_id, disciplina_id) VALUES (?, ?)", turma, id);
        return id;
    }

    private static DefinirHorariosRequest aulas(Horario... horarios) {
        return new DefinirHorariosRequest(List.of(horarios));
    }

    @Test
    void secretariaDefineEProfessorVeOHorarioDaDisciplinaDele() {
        horarioService.definir(turma, matematica, aulas(new Horario(1, SETE_E_MEIA), new Horario(3, SETE_E_MEIA)));
        // Substitui: a segunda definicao vale.
        List<HorarioAulaResponse> daTurma = horarioService.definir(turma, matematica,
                aulas(new Horario(1, SETE_E_MEIA), new Horario(5, SETE_E_MEIA)));
        assertThat(daTurma).extracting(HorarioAulaResponse::diaSemana).containsExactly(1, 5);

        autenticarComo(usuarioProfessor, "PROFESSOR", escola);
        TurmaDoProfessorResponse minha = portalProfessorService.listarMinhasTurmas().get(0);
        assertThat(minha.disciplinas()).singleElement().satisfies(d -> {
            assertThat(d.horarios()).extracting(HorarioAulaResponse::diaSemana).containsExactly(1, 5);
            assertThat(d.horarios().get(0).horaInicio()).isEqualTo(SETE_E_MEIA);
        });
    }

    @Test
    void turmaNaoTemDuasAulasNoMesmoHorarioNemDisciplinaForaDaGrade() {
        horarioService.definir(turma, matematica, aulas(new Horario(2, SETE_E_MEIA)));

        assertThatThrownBy(() -> horarioService.definir(turma, fisica, aulas(new Horario(2, SETE_E_MEIA))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outra aula");
        assertThatThrownBy(() -> horarioService.definir(turma, fisica,
                aulas(new Horario(4, SETE_E_MEIA), new Horario(4, SETE_E_MEIA))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetido");
        long foraDaGrade = jdbc.queryForObject("INSERT INTO disciplina (escola_id, nome, carga_horaria) "
                + "VALUES (?, 'Quimica', 60) RETURNING id", Long.class, escola);
        assertThatThrownBy(() -> horarioService.definir(turma, foraDaGrade, aulas(new Horario(4, SETE_E_MEIA))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grade");
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

        autenticarComo(usuarioProfessor, "PROFESSOR", escola);

        assertThat(portalProfessorService.alunosDaTurma(turma)).extracting(a -> a.nome())
                .containsExactly("Ana Lima", "Bruno Costa");
        assertThatThrownBy(() -> portalProfessorService.alunosDaTurma(outraTurma))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
