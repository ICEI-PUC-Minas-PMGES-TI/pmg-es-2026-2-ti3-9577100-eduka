package br.pucminas.eduka.backend.controller.escola;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.domain.escola.ModuloEscola;
import br.pucminas.eduka.backend.dto.escola.ContatoEscolaResponse;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.escola.EscolaService;

/** Contato da escola na Ajuda ({@code /minha-escola/contato}): filter chain e tokens reais. */
@WebMvcTest(ContatoEscolaController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class ContatoEscolaControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final String URL = "/minha-escola/contato";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EscolaService escolaService;

    @MockitoBean
    private EscolaRepository escolaRepository;

    private String bearer(String perfil, UUID escolaId) {
        return "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    @Test
    void todoPerfilDaEscolaVeOContatoDaPropriaEscola() throws Exception {
        // O FINANCEIRO so existe com a gestao financeira contratada (ModuloContratadoFilter).
        when(escolaRepository.modulos(ESCOLA))
                .thenReturn(Arrays.stream(ModuloEscola.values()).map(Enum::name).toList());
        when(escolaService.contato(ESCOLA)).thenReturn(new ContatoEscolaResponse(
                "Colegio Ipe Amarelo", "secretaria@ipe.com.br", "3133334444", "Belo Horizonte", "MG"));

        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(perfil, ESCOLA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Colegio Ipe Amarelo"))
                    .andExpect(jsonPath("$.email").value("secretaria@ipe.com.br"))
                    .andExpect(jsonPath("$.telefone").value("3133334444"))
                    .andExpect(jsonPath("$.cidade").value("Belo Horizonte"))
                    // So canais de contato: nada de CNPJ, plano ou situacao.
                    .andExpect(jsonPath("$.cnpjCpf").doesNotExist())
                    .andExpect(jsonPath("$.plano").doesNotExist());
        }
    }

    @Test
    void superAdminNaoTemEscolaENaoEntra() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer("SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
        verify(escolaService, never()).contato(any());
    }

    @Test
    void semTokenNaoEntra() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
