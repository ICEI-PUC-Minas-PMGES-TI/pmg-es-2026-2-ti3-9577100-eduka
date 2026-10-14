package br.pucminas.eduka.backend.dto;

import java.util.UUID;

/**
 * Responsavel visto a partir de um aluno: dados de contato mais o vinculo
 * (parentesco, se e o financeiro).
 *
 * @param pessoaId      id da pessoa; e o {@code responsavelId} das cobrancas
 * @param responsavelId id do cadastro em {@code /responsaveis}
 */
public record ResponsavelResponse(
        UUID pessoaId,
        UUID responsavelId,
        String nome,
        String email,
        String telefone,
        String parentesco,
        boolean financeiro) {
}
