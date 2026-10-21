package br.pucminas.eduka.backend.pagamento.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Conta devolvida por {@code POST/GET /v3/accounts}. A chave da conta filha
 * vem em {@code apiKey} na raiz; {@code accessToken} e um objeto com metadados
 * (achado A15 do spike). Na busca os dois vem nulos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContaAsaas(String id, String walletId, String apiKey, AccessToken accessToken) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccessToken(String apiKey) {

        @Override
        public String toString() {
            return "AccessToken[***]";
        }
    }

    /** Le {@code apiKey} da raiz e, se faltar, a de dentro de {@code accessToken}. */
    public String chave() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        return accessToken == null ? null : accessToken.apiKey();
    }

    @Override
    public String toString() {
        return "ContaAsaas[id=%s, walletId=%s]".formatted(id, walletId);
    }
}
