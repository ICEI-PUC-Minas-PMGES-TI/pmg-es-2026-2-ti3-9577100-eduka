package br.pucminas.eduka.backend.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * Tentativa de acessar um recurso de outra escola. Estende
 * {@link AccessDeniedException} para cair no mesmo tratamento (403) que uma
 * falha de {@code @PreAuthorize}.
 *
 * <p>
 * A mensagem nao diz qual escola e a do recurso: confirmar que o id existe
 * ja seria vazar informacao entre tenants.
 * </p>
 */
public class AcessoOutraEscolaException extends AccessDeniedException {

    public AcessoOutraEscolaException() {
        super("Recurso nao pertence a escola do usuario");
    }
}
