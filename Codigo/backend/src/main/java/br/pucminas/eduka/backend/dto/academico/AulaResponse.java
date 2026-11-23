package br.pucminas.eduka.backend.dto.academico;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.academico.Aula;

/** Uma aula com a chamada completa. */
public record AulaResponse(
        UUID id,
        Long turmaId,
        String turmaNome,
        Long disciplinaId,
        String disciplinaNome,
        LocalDate data,
        String conteudo,
        long presentes,
        long faltas,
        /** Ordenada pelo nome do aluno. */
        List<FrequenciaAlunoResponse> chamada,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static AulaResponse de(Aula aula, List<FrequenciaAlunoResponse> chamada) {
        long presentes = chamada.stream().filter(FrequenciaAlunoResponse::presente).count();
        return new AulaResponse(aula.getId(), aula.getTurma().getId(), aula.getTurma().getNome(),
                aula.getDisciplina().getId(), aula.getDisciplina().getNome(), aula.getData(), aula.getConteudo(),
                presentes, chamada.size() - presentes, chamada, aula.getCriadoEm(), aula.getAtualizadoEm());
    }
}
