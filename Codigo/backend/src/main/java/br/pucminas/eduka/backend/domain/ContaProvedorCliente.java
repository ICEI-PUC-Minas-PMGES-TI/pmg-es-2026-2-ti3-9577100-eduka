package br.pucminas.eduka.backend.domain;

/**
 * Em qual conta do provedor o cliente foi cadastrado (coluna {@code CONTA} de
 * {@code CLIENTE_PROVEDOR}, V26).
 *
 * <ul>
 * <li>FILHA: conta da escola; o cliente e um pagador de mensalidade (Fluxo B).</li>
 * <li>PAI: conta do Eduka; o cliente e a escola pagando a plataforma (Fluxo A).</li>
 * </ul>
 */
public enum ContaProvedorCliente {
    FILHA,
    PAI
}
