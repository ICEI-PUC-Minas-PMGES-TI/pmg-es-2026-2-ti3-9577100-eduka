package br.pucminas.eduka.backend.dto.acesso;

/**
 * Resposta do pedido de recuperacao. E a mesma para qualquer login, exista ou
 * nao: os valores vem da configuracao, nunca do pedido do usuario.
 *
 * @param validadeSegundos por quanto tempo o codigo enviado vale
 * @param reenvioSegundos  quanto esperar antes de pedir outro codigo
 */
public record RecuperacaoSenhaResponse(long validadeSegundos, long reenvioSegundos) {
}
