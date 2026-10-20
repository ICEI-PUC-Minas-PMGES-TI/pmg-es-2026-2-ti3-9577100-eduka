package br.pucminas.eduka.backend.domain;

/**
 * Onde a cobranca esta no provedor de pagamento (coluna
 * {@code SITUACAO_PROVEDOR}, V26). Independe do {@link StatusCobranca}: a
 * cobranca so vai para o provedor quando alguem clica em Pagar.
 *
 * <ul>
 * <li>NAO_EMITIDA: ainda nao existe no provedor.</li>
 * <li>EMITINDO: pedido de criacao em andamento ou sem resposta conclusiva;
 * ninguem cria outra enquanto isso.</li>
 * <li>EMITIDA: existe no provedor. Nunca volta; trocar a forma altera a
 * mesma cobranca.</li>
 * <li>FALHA_EMISSAO: o provedor recusou; o proximo Pagar tenta de novo.</li>
 * </ul>
 */
public enum SituacaoProvedor {
    NAO_EMITIDA,
    EMITINDO,
    EMITIDA,
    FALHA_EMISSAO
}
