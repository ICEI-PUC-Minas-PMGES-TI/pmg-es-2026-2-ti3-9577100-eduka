package br.pucminas.eduka.backend.dto.academico;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.Aula;

/** Uma aula na listagem: so os totais da chamada; a chamada inteira vem em {@link AulaResponse}. */
public record AulaResumoResponse(
        UUID id,
        Long turmaId,
        String turmaNome,
        Long disciplinaId,
        String disciplinaNome,
        LocalDate data,
        String conteudo,
        long presentes,
        long faltas,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static AulaResumoResponse de(Aula aula, long presentes, long faltas) {
        return new AulaResumoResponse(aula.getId(), aula.getTurma().getId(), aula.getTurma().getNome(),
                aula.getDisciplina().getId(), aula.getDisciplina().getNome(), aula.getData(), aula.getConteudo(),
                presentes, faltas, aula.getCriadoEm(), aula.getAtualizadoEm());
    }
}
