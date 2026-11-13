package br.pucminas.eduka.backend.exception;

/**
 * Codigo de recuperacao de senha que nao serve: errado, vencido, tentativas
 * esgotadas, nenhum pedido aberto ou login inexistente. A mensagem e a mesma
 * em todos os casos, para nao revelar quais logins existem.
 */
public class CodigoRecuperacaoInvalidoException extends RuntimeException {

    public CodigoRecuperacaoInvalidoException() {
        super("Codigo invalido ou expirado. Confira o codigo ou peca um novo");
    }
}
