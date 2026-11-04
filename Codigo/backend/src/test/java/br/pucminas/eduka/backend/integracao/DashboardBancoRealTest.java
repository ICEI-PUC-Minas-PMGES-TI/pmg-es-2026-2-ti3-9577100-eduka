package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.DashboardEscolaResponse;
import br.pucminas.eduka.backend.dto.DashboardSecretariaResponse;
import br.pucminas.eduka.backend.dto.DashboardSecretariaResponse.OcupacaoTurma;
import br.pucminas.eduka.backend.service.DashboardService;

/**
 * As contagens dos dashboards da escola e da secretaria, feitas no Postgres:
 * matriculas por situacao, alunos com matricula ativa contados uma vez so e
 * isolamento entre escolas.
 */
class DashboardBancoRealTest extends BancoRealTest {

    @Autowired
    private DashboardService dashboardService;

    private final LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Test
    void matriculasSaoContadasNoBancoSoDaEscola() {
        UUID escola = dados.escola();
        long turmaA = dados.turma(escola, "1A", 2);
        long turmaB = dados.turma(escola, "2B", 4);
        UUID ana = dados.aluno(escola);
        UUID bruno = dados.aluno(escola);
        UUID carla = dados.aluno(escola);
        UUID semTurma = dados.aluno(escola);
        dados.matricula(escola, ana, turmaA, StatusMatricula.ATIVA, hoje.minusDays(5));
        dados.matricula(escola, bruno, turmaA, StatusMatricula.ATIVA, hoje.minusDays(60));
        dados.matricula(escola, carla, turmaB, StatusMatricula.ATIVA, hoje.minusDays(30));
        // Ana tambem tem uma trancada: continua contando como uma aluna ativa so.
        dados.matricula(escola, ana, turmaB, StatusMatricula.TRANCADA, hoje.minusDays(90));
        dados.matricula(escola, semTurma, turmaB, StatusMatricula.CANCELADA, hoje.minusDays(31));

        UUID outra = dados.escola();
        dados.matricula(outra, dados.aluno(outra), dados.turma(outra, "1A", 10), StatusMatricula.ATIVA, hoje);

        autenticarComo("SECRETARIA", escola);
        DashboardSecretariaResponse secretaria = dashboardService.secretaria();

        assertThat(secretaria.matriculasPorSituacao()).containsEntry(StatusMatricula.ATIVA, 3L)
                .containsEntry(StatusMatricula.TRANCADA, 1L)
                .containsEntry(StatusMatricula.CANCELADA, 1L)
                .containsEntry(StatusMatricula.CONCLUIDA, 0L);
        // A de 30 dias atras entra; a de 31, nao.
        assertThat(secretaria.matriculasUltimos30Dias()).isEqualTo(2);
        assertThat(secretaria.alunosSemTurma()).isEqualTo(1);
        assertThat(secretaria.turmas()).extracting(OcupacaoTurma::turmaNome, OcupacaoTurma::matriculasAtivas)
                .containsExactly(tuple("1A", 2L), tuple("2B", 1L));
        assertThat(secretaria.vagas()).isEqualTo(new DashboardSecretariaResponse.Vagas(6, 3, 3, 1));

        autenticarComo("MASTER", escola);
        DashboardEscolaResponse painel = dashboardService.escola();

        assertThat(painel.pessoas().alunos()).isEqualTo(4);
        assertThat(painel.pessoas().alunosAtivos()).isEqualTo(3);
        assertThat(painel.pessoas().alunosSemTurma()).isEqualTo(1);
        assertThat(painel.estrutura().cursos()).isEqualTo(2);
        assertThat(painel.ocupacao()).isEqualTo(new DashboardEscolaResponse.Ocupacao(6, 3, 50));
    }

    @Test
    void aproveitamentoSomaAsNotasDaEscolaPeloValorDeCadaAvaliacao() {
        UUID escola = dados.escola();
        long turma = dados.turma(escola, "1A", 10);
        UUID ana = dados.aluno(escola);
        UUID bruno = dados.aluno(escola);
        UUID prova = dados.avaliacao(escola, turma, new BigDecimal("10.00"));
        UUID lista = dados.avaliacao(escola, turma, new BigDecimal("5.00"));
        dados.avaliacao(escola, turma, new BigDecimal("8.00")); // sem nota: conta como avaliacao, nao na soma
        dados.nota(escola, prova, ana, new BigDecimal("7.00"));
        dados.nota(escola, prova, bruno, new BigDecimal("8.00"));
        dados.nota(escola, lista, ana, new BigDecimal("5.00"));

        UUID outra = dados.escola();
        long turmaDeOutra = dados.turma(outra, "1A", 10);
        dados.nota(outra, dados.avaliacao(outra, turmaDeOutra, new BigDecimal("10.00")), dados.aluno(outra),
                BigDecimal.ZERO);

        autenticarComo("MASTER", escola);
        // 7 + 8 + 5 = 20 de 10 + 10 + 5 = 25 possiveis: 80%.
        assertThat(dashboardService.escola().desempenho())
                .isEqualTo(new DashboardEscolaResponse.Desempenho(3, 3, 80));

        autenticarComo("MASTER", dados.escola());
        assertThat(dashboardService.escola().desempenho())
                .isEqualTo(new DashboardEscolaResponse.Desempenho(0, 0, null));
    }
}
