package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.dto.academico.MatriculaTurmaRequest;
import br.pucminas.eduka.backend.dto.academico.PeriodoLetivoRequest;
import br.pucminas.eduka.backend.dto.academico.PeriodoLetivoResponse;
import br.pucminas.eduka.backend.service.academico.AcademicoSecretariaService;
import br.pucminas.eduka.backend.service.academico.MatriculaTurmaService;

/**
 * Encerrar periodo e abrir matriculas (IU09): periodo encerrado nao recebe
 * matricula; o planejado, so depois de Abrir matriculas.
 */
class PeriodoLetivoBancoRealTest extends BancoRealTest {

    @Autowired
    private AcademicoSecretariaService academicoService;
    @Autowired
    private MatriculaTurmaService matriculaService;

    private UUID escola;

    @BeforeEach
    void secretariaDaEscola() {
        escola = dados.escola();
        autenticarComo(dados.usuario(escola, "SECRETARIA", "Ana Secretaria"), "SECRETARIA", escola);
    }

    private PeriodoLetivoResponse periodo(LocalDate inicio, LocalDate fim) {
        PeriodoLetivoRequest request = new PeriodoLetivoRequest();
        request.setNome("Periodo " + UUID.randomUUID().toString().substring(0, 8));
        request.setDataInicio(inicio);
        request.setDataFim(fim);
        return academicoService.cadastrarPeriodoLetivo(escola, request);
    }

    /** Turma ativa no periodo, com um curso so dela. */
    private long turmaNo(PeriodoLetivoResponse periodo) {
        Long curso = jdbc.queryForObject("INSERT INTO curso (escola_id, nome) VALUES (?, ?) RETURNING id", Long.class,
                escola, "Curso " + UUID.randomUUID());
        return jdbc.queryForObject("INSERT INTO turma (escola_id, periodo_letivo_id, curso_id, nome, vagas, turno) "
                + "VALUES (?, ?, ?, '1A', 30, 'MATUTINO') RETURNING id", Long.class, escola, periodo.getId(), curso);
    }

    private void matricular(long turma) {
        matriculaService.matricular(new MatriculaTurmaRequest(dados.aluno(escola), turma, null, null));
    }

    @Test
    void periodoPlanejadoComecaFechadoESoRecebeMatriculaDepoisDeAbrir() {
        PeriodoLetivoResponse planejado = periodo(LocalDate.now().plusMonths(2), LocalDate.now().plusMonths(8));
        long turma = turmaNo(planejado);
        assertThat(planejado.isMatriculasAbertas()).isFalse();

        assertThatThrownBy(() -> matricular(turma)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ainda nao foram abertas");

        assertThat(academicoService.abrirMatriculas(escola, planejado.getId()).isMatriculasAbertas()).isTrue();
        matricular(turma);
    }

    @Test
    void periodoEmAndamentoEncerradoNaoRecebeMatriculaNova() {
        PeriodoLetivoResponse emAndamento = periodo(LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(4));
        long turma = turmaNo(emAndamento);
        assertThat(emAndamento.isMatriculasAbertas()).isTrue();
        matricular(turma);

        PeriodoLetivoResponse encerrado = academicoService.encerrarPeriodoLetivo(escola, emAndamento.getId());

        assertThat(encerrado.getEncerradoEm()).isNotNull();
        assertThatThrownBy(() -> matricular(turma)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encerrado");
        assertThatThrownBy(() -> academicoService.encerrarPeriodoLetivo(escola, emAndamento.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soEncerraEmAndamentoESoAbrePlanejado() {
        PeriodoLetivoResponse planejado = periodo(LocalDate.now().plusMonths(2), LocalDate.now().plusMonths(8));
        PeriodoLetivoResponse emAndamento = periodo(LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(4));

        assertThatThrownBy(() -> academicoService.encerrarPeriodoLetivo(escola, planejado.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> academicoService.abrirMatriculas(escola, emAndamento.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
