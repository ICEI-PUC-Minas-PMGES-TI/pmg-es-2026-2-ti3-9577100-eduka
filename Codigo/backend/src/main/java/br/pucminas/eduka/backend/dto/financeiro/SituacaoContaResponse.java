package br.pucminas.eduka.backend.dto.financeiro;

/** As quatro dimensoes do cadastro da conta no provedor. */
public record SituacaoContaResponse(String geral, String documentacao, String dadosComerciais,
        String contaBancaria) {
}
