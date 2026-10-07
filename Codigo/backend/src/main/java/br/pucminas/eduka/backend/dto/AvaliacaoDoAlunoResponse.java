package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.Avaliacao;
import br.pucminas.eduka.backend.domain.Nota;
import br.pucminas.eduka.backend.domain.TipoAvaliacao;

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
        String observacao) {

    public static AvaliacaoDoAlunoResponse de(Avaliacao avaliacao, Nota nota) {
        return new AvaliacaoDoAlunoResponse(avaliacao.getId(), avaliacao.getTurma().getId(),
                avaliacao.getTurma().getNome(), avaliacao.getDisciplina().getId(),
                avaliacao.getDisciplina().getNome(), avaliacao.getTipo(), avaliacao.getTitulo(),
                avaliacao.getDescricao(), avaliacao.getData(), avaliacao.getValor(),
                nota != null ? nota.getValor() : null, nota != null ? nota.getObservacao() : null);
    }
}
