package br.pucminas.eduka.backend.dto;

/**
 * Dados de cadastro de escola. {@code id}, {@code criadoEm} e {@code versao}
 * nao entram: sao geridos pelo banco/Hibernate.
 *
 * <p>
 * {@code ativo} omitido (nulo) vira {@code true}.
 * </p>
 */
public record EscolaRequest(
        String nome,
        String cnpjCpf,
        String endereco,
        String email,
        String telefone,
        Boolean ativo) {
}
