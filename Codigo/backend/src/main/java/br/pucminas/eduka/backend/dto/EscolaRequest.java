package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domain.PlanoEscola;

/**
 * Dados de cadastro de escola. {@code id}, {@code criadoEm} e {@code versao}
 * nao entram: sao geridos pelo banco/Hibernate.
 *
 * <p>
 * {@code ativo} omitido (nulo) vira {@code true}. {@code plano} e obrigatorio.
 * </p>
 */
public record EscolaRequest(
        String nome,
        String cnpjCpf,
        String endereco,
        String email,
        String telefone,
        PlanoEscola plano,
        Boolean ativo) {
}
