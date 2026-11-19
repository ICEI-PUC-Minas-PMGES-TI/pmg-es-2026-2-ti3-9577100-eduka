package br.pucminas.eduka.backend.dto.academico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.Avaliacao;
import br.pucminas.eduka.backend.domain.academico.Bimestres;
import br.pucminas.eduka.backend.domain.academico.Nota;
import br.pucminas.eduka.backend.domain.academico.TipoAvaliacao;

/**
 * Uma avaliacao vista pelo aluno: a prova/trabalho/tarefa e a nota dele, que
 * vem nula enquanto o professor nao lancou.
 */
public record AvaliacaoDoAlunoResponse(
        UUID id,
        Long turmaId,
        String turmaNome,
        Long disciplinaId,
        String disciplinaNome,
        TipoAvaliacao tipo,
        String titulo,
        String descricao,
        LocalDate data,
        /** Pontuacao maxima. */
        BigDecimal valor,
        BigDecimal nota,
        String observacao,
        /** Bimestre (1 a 4) pela data, no periodo letivo da turma; nulo se o periodo nao tem datas. */
        Integer bimestre) {

    public static AvaliacaoDoAlunoResponse de(Avaliacao avaliacao, Nota nota) {
        return de(avaliacao, nota, Bimestres.doPeriodo(avaliacao.getTurma().getPeriodoLetivo()));
    }

    /** Com os bimestres da turma ja calculados (o boletim calcula uma vez por turma). */
    public static AvaliacaoDoAlunoResponse de(Avaliacao avaliacao, Nota nota, Bimestres bimestres) {
        return new AvaliacaoDoAlunoResponse(avaliacao.getId(), avaliacao.getTurma().getId(),
                avaliacao.getTurma().getNome(), avaliacao.getDisciplina().getId(),
                avaliacao.getDisciplina().getNome(), avaliacao.getTipo(), avaliacao.getTitulo(),
                avaliacao.getDescricao(), avaliacao.getData(), avaliacao.getValor(),
                nota != null ? nota.getValor() : null, nota != null ? nota.getObservacao() : null,
                bimestres.numeroDe(avaliacao.getData()));
    }
}
