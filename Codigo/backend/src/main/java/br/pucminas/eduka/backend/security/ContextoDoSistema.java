package br.pucminas.eduka.backend.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Para rotinas do proprio Eduka (sem requisicao nem token) que precisam ler e
 * gravar entidades de uma escola. O tenant do Hibernate sai do
 * {@code SecurityContext} ({@link EscolaTenantIdentifierResolver}); sem
 * escola nele, as entidades particionadas voltam vazias.
 *
 * <p>
 * O tenant e lido quando a sessao do Hibernate abre, isto e, no inicio da
 * transacao: a transacao tem que comecar <em>dentro</em> de
 * {@link #naEscola}, nunca antes.
 * </p>
 */
public final class ContextoDoSistema {

    /** Perfil do principal das rotinas: nao e perfil de usuario e nao passa em nenhum {@code @PreAuthorize}. */
    public static final String PERFIL = "SISTEMA";

    /** Id do principal das rotinas: nao e de nenhum usuario. */
    public static final UUID USUARIO = new UUID(0L, 0L);

    private ContextoDoSistema() {
    }

    /** Roda {@code acao} com a escola no contexto e depois restaura o contexto anterior. */
    public static void naEscola(UUID escolaId, Runnable acao) {
        SecurityContext anterior = SecurityContextHolder.getContext();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        UsuarioAutenticado principal = new UsuarioAutenticado(USUARIO, PERFIL, escolaId);
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        SecurityContextHolder.setContext(contexto);
        try {
            acao.run();
        } finally {
            SecurityContextHolder.setContext(anterior);
        }
    }
}
