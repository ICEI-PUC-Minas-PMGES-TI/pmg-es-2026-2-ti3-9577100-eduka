package br.pucminas.eduka.backend.exception.acesso;

/**
 * O refresh token nao renova mais a sessao: vencido, adulterado, de usuario
 * inativo ou emitido antes da ultima troca de senha. Os casos respondem igual;
 * em todos o caminho e entrar de novo pelo login.
 */
public class SessaoExpiradaException extends RuntimeException {

    public SessaoExpiradaException() {
        super("Sessao expirada. Entre novamente.");
    }
}
