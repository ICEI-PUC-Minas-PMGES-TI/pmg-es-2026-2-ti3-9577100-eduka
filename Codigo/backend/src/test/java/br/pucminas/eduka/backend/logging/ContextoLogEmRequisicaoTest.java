package br.pucminas.eduka.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * Com a cadeia real (RequestIdFilter + Spring Security + EscolaLogFilter),
 * confere o que o MDC tem enquanto o controller roda: requestId sempre,
 * escolaId so para usuario que tem escola.
 */
@WebMvcTest(ContextoLogEmRequisicaoTest.MdcController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        RequestIdFilter.class, ContextoLogEmRequisicaoTest.MdcController.class })
class ContextoLogEmRequisicaoTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @RestController
    static class MdcController {
        @GetMapping("/teste/mdc")
        Map<String, String> mdc() {
            LoggerFactory.getLogger(MdcController.class).info("requisicao de teste tratada");
            Map<String, String> corpo = new HashMap<>();
            corpo.put("requestId", MDC.get(ContextoLog.REQUEST_ID));
            corpo.put("escolaId", MDC.get(ContextoLog.ESCOLA_ID));
            return corpo;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String token(String perfil, UUID escolaId) {
        return "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    @Test
    void usuarioAutenticadoDeUmaEscolaTemRequestIdEEscolaIdNoMdc() throws Exception {
        mockMvc.perform(get("/teste/mdc")
                .header(HttpHeaders.AUTHORIZATION, token("MASTER", ESCOLA))
                .header("X-Request-Id", "req-do-cliente-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-do-cliente-1"))
                .andExpect(jsonPath("$.requestId").value("req-do-cliente-1"))
                .andExpect(jsonPath("$.escolaId").value(ESCOLA.toString()));

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void superAdminSemEscolaFicaSemEscolaId() throws Exception {
        mockMvc.perform(get("/teste/mdc").header(HttpHeaders.AUTHORIZATION, token("SUPER_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.escolaId").doesNotExist());
    }

    @Test
    void semTokenARespostaDeErroTambemLevaORequestId() throws Exception {
        mockMvc.perform(get("/teste/mdc"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
