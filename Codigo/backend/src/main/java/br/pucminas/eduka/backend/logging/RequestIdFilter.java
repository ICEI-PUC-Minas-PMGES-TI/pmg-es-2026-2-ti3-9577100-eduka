package br.pucminas.eduka.backend.logging;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Primeiro filtro da cadeia (antes do Spring Security): da um id a cada
 * requisicao, poe no MDC para sair em todas as linhas de log dela e devolve no
 * header {@value #HEADER}, para o suporte achar no log a requisicao que o
 * usuario viu falhar. Aproveita o id que o cliente mandou so se for seguro
 * (letras, numeros e hifen, ate 64 caracteres); senao gera um UUID. Isso
 * impede que um header forjado injete quebra de linha ou JSON no log.
 *
 * <p>
 * O {@code escolaId} entra depois, em {@link EscolaLogFilter}, quando o token
 * ja foi validado.
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    private static final Pattern ID_SEGURO = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || !ID_SEGURO.matcher(id).matches()) {
            id = ContextoLog.novoId();
        }
        MDC.put(ContextoLog.REQUEST_ID, id);
        response.setHeader(HEADER, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            ContextoLog.limpar();
        }
    }
}
