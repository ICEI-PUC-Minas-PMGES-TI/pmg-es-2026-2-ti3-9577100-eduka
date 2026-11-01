package br.pucminas.eduka.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class RequestIdFilterTest {

    private final RequestIdFilter filtro = new RequestIdFilter();
    private final MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/qualquer");
    private final MockHttpServletResponse resposta = new MockHttpServletResponse();

    @AfterEach
    void limpar() {
        MDC.clear();
    }

    /** Cadeia que guarda o requestId visto no MDC enquanto a requisicao esta em andamento. */
    private static FilterChain cadeiaQueLe(AtomicReference<String> visto) {
        return (req, res) -> visto.set(MDC.get(ContextoLog.REQUEST_ID));
    }

    @Test
    void headerSeguroEReaproveitadoNoMdcENaResposta() throws Exception {
        requisicao.addHeader("X-Request-Id", "abc-123-DEF");
        AtomicReference<String> visto = new AtomicReference<>();

        filtro.doFilter(requisicao, resposta, cadeiaQueLe(visto));

        assertThat(visto.get()).isEqualTo("abc-123-DEF");
        assertThat(resposta.getHeader("X-Request-Id")).isEqualTo("abc-123-DEF");
    }

    @Test
    void headerDeExatamente64CaracteresEAceito() throws Exception {
        String id = "a".repeat(64);
        requisicao.addHeader("X-Request-Id", id);
        AtomicReference<String> visto = new AtomicReference<>();

        filtro.doFilter(requisicao, resposta, cadeiaQueLe(visto));

        assertThat(visto.get()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com espaco",
            "com\nquebra",
            "{\"json\":\"injetado\"}",
            "acentuaçao",
            "a_b",
            "../../etc",
            "" })
    void headerInseguroOuVazioETrocadoPorUuid(String inseguro) throws Exception {
        requisicao.addHeader("X-Request-Id", inseguro);
        AtomicReference<String> visto = new AtomicReference<>();

        filtro.doFilter(requisicao, resposta, cadeiaQueLe(visto));

        assertThat(visto.get()).isNotEqualTo(inseguro);
        assertThat(UUID.fromString(visto.get())).isNotNull();
        assertThat(resposta.getHeader("X-Request-Id")).isEqualTo(visto.get());
    }

    @Test
    void headerComMaisDe64CaracteresETrocadoPorUuid() throws Exception {
        requisicao.addHeader("X-Request-Id", "a".repeat(65));
        AtomicReference<String> visto = new AtomicReference<>();

        filtro.doFilter(requisicao, resposta, cadeiaQueLe(visto));

        assertThat(UUID.fromString(visto.get())).isNotNull();
    }

    @Test
    void semHeaderGeraUuidEDevolveNaResposta() throws Exception {
        AtomicReference<String> visto = new AtomicReference<>();

        filtro.doFilter(requisicao, resposta, cadeiaQueLe(visto));

        assertThat(UUID.fromString(visto.get())).isNotNull();
        assertThat(resposta.getHeader("X-Request-Id")).isEqualTo(visto.get());
    }

    @Test
    void cadaRequisicaoSemHeaderGanhaUmIdDiferente() throws Exception {
        AtomicReference<String> primeiro = new AtomicReference<>();
        AtomicReference<String> segundo = new AtomicReference<>();

        filtro.doFilter(requisicao, resposta, cadeiaQueLe(primeiro));
        filtro.doFilter(new MockHttpServletRequest("GET", "/outra"), new MockHttpServletResponse(),
                cadeiaQueLe(segundo));

        assertThat(primeiro.get()).isNotEqualTo(segundo.get());
    }

    @Test
    void mdcFicaLimpoDepoisDaRequisicao() throws Exception {
        filtro.doFilter(requisicao, resposta, new MockFilterChain());
        assertThat(MDC.get(ContextoLog.REQUEST_ID)).isNull();
        assertThat(MDC.get(ContextoLog.ESCOLA_ID)).isNull();
    }

    @Test
    void mdcFicaLimpoMesmoQuandoACadeiaLancaExcecao() {
        FilterChain quebra = (req, res) -> {
            MDC.put(ContextoLog.ESCOLA_ID, UUID.randomUUID().toString());
            throw new IllegalStateException("falha na cadeia");
        };

        assertThatThrownBy(() -> filtro.doFilter(requisicao, resposta, quebra))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(ContextoLog.REQUEST_ID)).isNull();
        assertThat(MDC.get(ContextoLog.ESCOLA_ID)).isNull();
    }
}
