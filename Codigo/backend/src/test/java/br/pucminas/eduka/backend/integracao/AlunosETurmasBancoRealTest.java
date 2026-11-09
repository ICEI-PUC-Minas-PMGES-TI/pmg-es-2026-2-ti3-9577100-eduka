package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.dto.TurmaResponse;
import br.pucminas.eduka.backend.service.AcademicoSecretariaService;
import br.pucminas.eduka.backend.service.AlunoService;

/**
 * As listagens que as telas de Alunos e Matriculas usam no lugar do historico
 * de matriculas: a situacao de cada aluno e as matriculas ativas de cada
 * turma, contadas no Postgres.
 */
class AlunosETurmasBancoRealTest extends BancoRealTest {

    @Autowired
    private AlunoService alunoService;
    @Autowired
    private AcademicoSecretariaService academicoService;

    @Test
    void listagensTrazemASituacaoDosAlunosEAsVagasOcupadasDeCadaTurma() {
        UUID escola = dados.escola();
        long turmaA = dados.turma(escola, "1A", 2);
        long turmaB = dados.turma(escola, "2B", 4);
        long antiga = dados.turma(escola, "Antiga", 30);
        UUID ana = dados.aluno(escola, "Ana");
        UUID bia = dados.aluno(escola, "Bia");
        UUID caio = dados.aluno(escola, "Caio");
        LocalDate hoje = LocalDate.now();
        // Ana: trancada na 2B e ativa na 1A. Bia: so trancada. Caio: so uma concluida antiga.
        dados.matricula(escola, ana, turmaB, StatusMatricula.TRANCADA, hoje);
        dados.matricula(escola, ana, turmaA, StatusMatricula.ATIVA, hoje);
        dados.matricula(escola, bia, turmaB, StatusMatricula.TRANCADA, hoje);
        dados.matricula(escola, caio, antiga, StatusMatricula.CONCLUIDA, hoje.minusYears(1));

        UUID outra = dados.escola();
        dados.matricula(outra, dados.aluno(outra), dados.turma(outra, "1A", 9), StatusMatricula.ATIVA, hoje);

        autenticarComo("SECRETARIA", escola);

        assertThat(alunoService.listar(null))
                .extracting(AlunoResponse::nome, AlunoResponse::situacao, AlunoResponse::turmaId,
                        AlunoResponse::turmaNome)
                .containsExactlyInAnyOrder(
                        tuple("Ana", AlunoResponse.Situacao.MATRICULADO, turmaA, "1A"),
                        tuple("Bia", AlunoResponse.Situacao.TRANCADO, turmaB, "2B"),
                        tuple("Caio", AlunoResponse.Situacao.SEM_TURMA, null, null));

        assertThat(academicoService.listarTurmas(escola))
                .extracting(TurmaResponse::getNome, TurmaResponse::getMatriculasAtivas)
                .containsExactlyInAnyOrder(tuple("1A", 1L), tuple("2B", 0L), tuple("Antiga", 0L));
    }
}
