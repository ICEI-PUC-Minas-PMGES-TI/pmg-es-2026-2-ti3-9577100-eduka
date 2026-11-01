package br.pucminas.eduka.backend.logging;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Poe o {@code escolaId} do usuario autenticado no MDC. Entra na cadeia do
 * Spring Security logo depois da validacao do JWT (ver {@code JwtConfig}); por
 * isso nao e um bean: como bean, o Spring Boot o registraria tambem como filtro
 * de servlet, antes da autenticacao. Rota publica e {@code SUPER_ADMIN} nao tem
 * escola, e ficam sem o campo. O {@link RequestIdFilter} limpa o MDC no fim.
 */
public class EscolaLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UsuarioAutenticado usuario) {
            ContextoLog.definirEscola(usuario.escolaId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            ContextoLog.limparEscola();
        }
    }
}
