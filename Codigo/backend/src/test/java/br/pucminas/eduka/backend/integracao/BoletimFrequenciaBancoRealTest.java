package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.BoletimTurmaResponse;
import br.pucminas.eduka.backend.dto.BoletimTurmaResponse.BoletimDisciplina;
import br.pucminas.eduka.backend.service.PortalAlunoService;

/**
 * Frequencia no boletim do aluno, ponta a ponta no Postgres: aulas e faltas
 * por disciplina a partir de AULA e FREQUENCIA, "sem aulas" como nulo e o
 * alerta de faltas acima de 25%.
 */
class BoletimFrequenciaBancoRealTest extends BancoRealTest {

    @Autowired
    private PortalAlunoService portalAlunoService;

    private UUID escola;
    private long turma;
    private long matematica;
    private long fisica;
    private long artes;
    private UUID professor;
    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void turmaComTresDisciplinasEDoisAlunos() {
        escola = dados.escola();
        turma = dados.turma(escola, "1A", 30);
        matematica = disciplinaNaGrade("Matematica");
        fisica = disciplinaNaGrade("Fisica");
        artes = disciplinaNaGrade("Artes");
        UUID professorUsuario = dados.usuario(escola, "PROFESSOR", "Paulo Lima");
        professor = jdbc.queryForObject("INSERT INTO PROFESSOR (ESCOLA_ID, PESSOA_ID, USUARIO_ID) "
                + "SELECT ESCOLA_ID, PESSOA_ID, ID_USUARIO FROM USUARIO WHERE ID_USUARIO = ? RETURNING ID_PROFESSOR",
                UUID.class, professorUsuario);
        var alunaComAcesso = dados.alunoComAcesso(escola, "Ana Souza", "52998224725");
        ana = alunaComAcesso.alunoId();
        bruno = dados.aluno(escola, "Bruno Alves");
        dados.matricula(escola, ana, turma, StatusMatricula.ATIVA, LocalDate.now().minusDays(30));
        dados.matricula(escola, bruno, turma, StatusMatricula.ATIVA, LocalDate.now().minusDays(30));
        autenticarComo(alunaComAcesso.usuarioId(), "ALUNO", escola);
    }

    private long disciplinaNaGrade(String nome) {
        Long id = jdbc.queryForObject("INSERT INTO disciplina (escola_id, nome, carga_horaria) VALUES (?, ?, 60) "
                + "RETURNING id", Long.class, escola, nome + " " + DadosDeTeste.unico());
        jdbc.update("INSERT INTO turma_disciplina (turma_id, disciplina_id) VALUES (?, ?)", turma, id);
        return id;
    }

    /** Uma aula com a chamada das duas alunas; o dia so serve para as aulas serem distintas. */
    private void aula(long disciplina, int diasAtras, boolean anaPresente, boolean brunoPresente) {
        UUID aula = jdbc.queryForObject("INSERT INTO AULA (ESCOLA_ID, TURMA_ID, DISCIPLINA_ID, PROFESSOR_ID, "
                + "DATA_AULA, CONTEUDO) VALUES (?, ?, ?, ?, ?, 'Conteudo') RETURNING ID_AULA", UUID.class,
                escola, turma, disciplina, professor, LocalDate.now().minusDays(diasAtras));
        chamada(aula, ana, anaPresente);
        chamada(aula, bruno, brunoPresente);
    }

    private void chamada(UUID aula, UUID aluno, boolean presente) {
        jdbc.update("INSERT INTO FREQUENCIA (ESCOLA_ID, AULA_ID, ALUNO_ID, PRESENTE) VALUES (?, ?, ?, ?)",
                escola, aula, aluno, presente);
    }

    private BoletimDisciplina disciplina(BoletimTurmaResponse boletim, long id) {
        return boletim.disciplinas().stream().filter(d -> d.disciplinaId() == id).findFirst().orElseThrow();
    }

    @Test
    void somaAulasEFaltasDoAlunoPorDisciplinaEMarcaSemAulasComoNulo() {
        // Matematica: 4 aulas e 1 falta da Ana = exatamente 25%, sem alerta.
        aula(matematica, 4, true, false);
        aula(matematica, 3, true, true);
        aula(matematica, 2, false, true);
        aula(matematica, 1, true, false);
        // Fisica: 2 aulas e 1 falta da Ana = 50%, alerta. Artes: nenhuma aula.
        aula(fisica, 2, false, true);
        aula(fisica, 1, true, true);

        var boletim = portalAlunoService.boletim();

        assertThat(boletim).hasSize(1);
        BoletimTurmaResponse turmaDaAna = boletim.get(0);
        assertThat(turmaDaAna.limiteFaltasPercentual()).isEqualTo(25);

        var mat = disciplina(turmaDaAna, matematica).frequencia();
        assertThat(mat.totalAulas()).isEqualTo(4);
        assertThat(mat.faltas()).isEqualTo(1);
        assertThat(mat.percentualFrequencia()).isEqualByComparingTo("75.0");
        assertThat(mat.alertaFaltas()).isFalse();

        var fis = disciplina(turmaDaAna, fisica).frequencia();
        assertThat(fis.totalAulas()).isEqualTo(2);
        assertThat(fis.faltas()).isEqualTo(1);
        assertThat(fis.percentualFrequencia()).isEqualByComparingTo(new BigDecimal("50.0"));
        assertThat(fis.alertaFaltas()).isTrue();

        assertThat(disciplina(turmaDaAna, artes).frequencia()).isNull();

        var geral = turmaDaAna.frequencia();
        assertThat(geral.totalAulas()).isEqualTo(6);
        assertThat(geral.faltas()).isEqualTo(2);
        assertThat(geral.percentualFrequencia()).isEqualByComparingTo("66.7");
        assertThat(geral.alertaFaltas()).isTrue();
    }

    @Test
    void turmaSemNenhumaAulaNaoTemFrequencia() {
        var turmaDaAna = portalAlunoService.boletim().get(0);

        assertThat(turmaDaAna.frequencia()).isNull();
        assertThat(turmaDaAna.disciplinas()).extracting(BoletimDisciplina::frequencia).containsOnlyNulls();
    }
}
