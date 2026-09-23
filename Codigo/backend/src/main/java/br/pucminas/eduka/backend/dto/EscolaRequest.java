package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domain.PlanoEscola;
import jakarta.validation.Valid;

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
        @Valid EnderecoDto endereco,
        String email,
        String telefone,
        PlanoEscola plano,
        Boolean ativo) {
}
