package br.pucminas.eduka.backend.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * O usuario pode usar a rota, mas nao para esta acao: por exemplo, uma
 * secretaria tentando criar um MASTER. Estende {@link AccessDeniedException}
 * para responder 403 como qualquer falha de autorizacao.
 */
public class PerfilNaoPermitidoException extends AccessDeniedException {

    public PerfilNaoPermitidoException(String mensagem) {
        super(mensagem);
    }
}
