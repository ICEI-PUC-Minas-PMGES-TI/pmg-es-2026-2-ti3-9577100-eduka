package br.pucminas.eduka.backend.controller.escola;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.domain.escola.PlanoEscola;
import br.pucminas.eduka.backend.dto.escola.AssinaturaResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.escola.EscolaService;

/** So o MASTER le a assinatura, e sempre a da escola do token. */
@WebMvcTest(AssinaturaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class AssinaturaControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EscolaService escolaService;

    private String token(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    @Test
    void masterLeAAssinaturaDaPropriaEscola() throws Exception {
        when(escolaService.assinatura(ESCOLA)).thenReturn(new AssinaturaResponse("Colegio Horizonte",
                PlanoEscola.values()[0], true, LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15), 1500));

        mockMvc.perform(get("/config-pagamento/assinatura")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("MASTER", ESCOLA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escolaNome").value("Colegio Horizonte"))
                .andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.limiteUsuarios").value(1500));
    }

    @Test
    void outrosPerfisNaoLeem() throws Exception {
        for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(get("/config-pagamento/assinatura")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(perfil, ESCOLA)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/config-pagamento/assinatura")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
        verify(escolaService, never()).assinatura(any());
    }
}
