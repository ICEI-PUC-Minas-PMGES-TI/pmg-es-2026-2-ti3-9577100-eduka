package br.pucminas.eduka.backend.dto.academico;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.Avaliacao;
import br.pucminas.eduka.backend.domain.academico.TipoAvaliacao;

public record AvaliacaoResponse(
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
        /** Quantos alunos ja tem nota lancada nesta avaliacao. */
        long notasLancadas,
        /** Falso enquanto as notas estao em rascunho: o aluno ainda nao as ve. */
        boolean notasPublicadas,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static AvaliacaoResponse de(Avaliacao avaliacao, long notasLancadas) {
        return new AvaliacaoResponse(avaliacao.getId(), avaliacao.getTurma().getId(), avaliacao.getTurma().getNome(),
                avaliacao.getDisciplina().getId(), avaliacao.getDisciplina().getNome(), avaliacao.getTipo(),
                avaliacao.getTitulo(), avaliacao.getDescricao(), avaliacao.getData(), avaliacao.getValor(),
                notasLancadas, avaliacao.isNotasPublicadas(), avaliacao.getCriadoEm(), avaliacao.getAtualizadoEm());
    }
}
