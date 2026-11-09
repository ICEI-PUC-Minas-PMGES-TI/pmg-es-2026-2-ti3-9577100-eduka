package br.pucminas.eduka.backend.security;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import br.pucminas.eduka.backend.exception.EscolaSuspensaException;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Barra toda requisicao de usuario cuja escola foi suspensa depois do login
 * (RF24): o token continua valido, mas a escola nao. Responde o mesmo 403
 * {@code ESCOLA_SUSPENSA} do login, e o front leva para o aviso (IU04).
 *
 * <p>
 * Entra na cadeia do Spring Security logo depois do JWT validado (ver
 * {@link JwtConfig}); por isso nao e bean. {@code SUPER_ADMIN} nao tem escola
 * e passa direto. Escola que nao existe tambem passa: o isolamento por escola
 * ja responde por ela. O repositorio vem por {@link ObjectProvider} para os
 * testes de controller, que sobem so a camada web, continuarem sem ele.
 * </p>
 */
public class EscolaSuspensaFilter extends OncePerRequestFilter {

    private final ObjectProvider<EscolaRepository> escolaRepository;
    private final HandlerExceptionResolver resolver;

    public EscolaSuspensaFilter(ObjectProvider<EscolaRepository> escolaRepository,
            HandlerExceptionResolver resolver) {
        this.escolaRepository = escolaRepository;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        EscolaRepository repositorio = escolaRepository.getIfAvailable();
        if (repositorio != null && authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado usuario && usuario.escolaId() != null) {
            EscolaRepository.Situacao situacao = repositorio.situacao(usuario.escolaId()).orElse(null);
            if (situacao != null && !situacao.isAtiva()) {
                resolver.resolveException(request, response, null, new EscolaSuspensaException(situacao.getMotivo()));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
