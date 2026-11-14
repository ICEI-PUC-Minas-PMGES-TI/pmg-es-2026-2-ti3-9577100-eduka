package br.pucminas.eduka.backend.exception.acesso;

/**
 * A verificacao nao existe mais: id desconhecido, tentativas esgotadas ou
 * substituida por um login mais novo. A mensagem e a mesma nos tres casos, e o
 * unico caminho e entrar de novo com a senha.
 */
public class VerificacaoLoginEncerradaException extends RuntimeException {

    public VerificacaoLoginEncerradaException() {
        super("Verificacao encerrada. Faca o login novamente");
    }
}
