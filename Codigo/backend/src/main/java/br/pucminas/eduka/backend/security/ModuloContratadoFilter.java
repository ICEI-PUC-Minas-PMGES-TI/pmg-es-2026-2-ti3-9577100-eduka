package br.pucminas.eduka.backend.security;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import br.pucminas.eduka.backend.domain.escola.ModuloEscola;
import br.pucminas.eduka.backend.exception.escola.ModuloNaoContratadoException;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Barra a requisicao que depende de um modulo que a escola do usuario nao
 * contratou (ver {@link RotasPorModulo}): pela rota (ex.: cobrancas sem a
 * gestao financeira) ou pelo perfil (ex.: FINANCEIRO, que so existe com ela).
 * Responde 403 {@code MODULO_NAO_CONTRATADO}.
 *
 * <p>
 * O login ja barra o perfil sem modulo; este filtro cobre o token emitido
 * antes de o SUPER_ADMIN tirar o modulo. So consulta o banco quando a rota ou
 * o perfil dependem de modulo. Como o {@link EscolaSuspensaFilter}, entra na
 * cadeia do Spring Security (ver {@link JwtConfig}) e nao e bean.
 * </p>
 */
public class ModuloContratadoFilter extends OncePerRequestFilter {

    private final ObjectProvider<EscolaRepository> escolaRepository;
    private final HandlerExceptionResolver resolver;

    public ModuloContratadoFilter(ObjectProvider<EscolaRepository> escolaRepository,
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
            Set<ModuloEscola> exigidos = EnumSet.noneOf(ModuloEscola.class);
            RotasPorModulo.exigidoPeloPerfil(usuario.perfilUsuario()).ifPresent(exigidos::add);
            exigidos.addAll(RotasPorModulo.exigidosPelaRota(caminho(request)));
            if (!exigidos.isEmpty()) {
                Set<ModuloEscola> contratados = ModuloEscola.dosNomes(repositorio.modulos(usuario.escolaId()));
                for (ModuloEscola modulo : exigidos) {
                    if (!contratados.contains(modulo)) {
                        resolver.resolveException(request, response, null, new ModuloNaoContratadoException(modulo));
                        return;
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Caminho sem o context path, como nos padroes de {@link RotasPorModulo}. */
    private static String caminho(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        return contexto != null && !contexto.isEmpty() && uri.startsWith(contexto) ? uri.substring(contexto.length())
                : uri;
    }
}
