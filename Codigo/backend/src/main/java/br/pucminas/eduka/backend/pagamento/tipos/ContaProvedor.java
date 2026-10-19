package br.pucminas.eduka.backend.pagamento.tipos;

/**
 * Conta da escola no provedor. {@code credencial} so vem na criacao; na busca
 * por CNPJ o provedor nao devolve a chave e ela fica nula.
 */
public record ContaProvedor(String idContaExterna, String walletId, CredencialProvedor credencial) {
}
