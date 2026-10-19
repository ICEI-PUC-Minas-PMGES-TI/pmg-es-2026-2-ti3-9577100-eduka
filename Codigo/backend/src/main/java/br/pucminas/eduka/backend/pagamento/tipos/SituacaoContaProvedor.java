package br.pucminas.eduka.backend.pagamento.tipos;

/** As quatro dimensoes do cadastro da conta no provedor. */
public record SituacaoContaProvedor(
        SituacaoCadastral geral,
        SituacaoCadastral documentacao,
        SituacaoCadastral dadosComerciais,
        SituacaoCadastral contaBancaria) {
}
