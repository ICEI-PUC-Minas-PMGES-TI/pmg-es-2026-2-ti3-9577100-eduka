package br.pucminas.eduka.backend.exception.acesso;

/**
 * Pedido de novo envio de primeiro acesso para quem ja trocou a senha
 * provisoria pela definitiva: reenviar sobrescreveria a senha do usuario.
 */
public class AcessoJaAtivadoException extends RuntimeException {

    public AcessoJaAtivadoException() {
        super("Usuario ja definiu a senha definitiva");
    }
}
