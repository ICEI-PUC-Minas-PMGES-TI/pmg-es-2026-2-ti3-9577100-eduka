package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domains.enums.Status;

/**
 * Dados de cadastro de escola. {@code id}, {@code criadoEm} e
 * {@code atualizadoEm} nao entram: sao gerados pelo banco.
 *
 * <p>
 * {@code email} e {@code telefone} sao o contato da escola (coluna
 * {@code email_responsavel} no banco). {@code status} omitido vira
 * {@code ATIVO}.
 * </p>
 */
public record EscolaRequest(
        String nome,
        String cnpj,
        String endereco,
        String email,
        String telefone,
        Status status) {
}
