package br.pucminas.eduka.backend.dto;

import java.util.Collection;

/**
 * Professores da escola inteira, para os cards da tela Professores (IU08).
 *
 * @param semVinculo cadastros sem o vinculo (CLT, PJ...) informado
 */
public record ResumoProfessoresResponse(long total, long ativos, long semVinculo) {

    public static ResumoProfessoresResponse de(Collection<ProfessorResponse> professores) {
        long ativos = professores.stream().filter(ProfessorResponse::ativo).count();
        long semVinculo = professores.stream().filter(p -> p.vinculo() == null).count();
        return new ResumoProfessoresResponse(professores.size(), ativos, semVinculo);
    }
}
