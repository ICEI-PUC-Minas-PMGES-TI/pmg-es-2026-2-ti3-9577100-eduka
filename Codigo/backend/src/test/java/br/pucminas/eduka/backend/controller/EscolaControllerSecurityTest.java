package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.EscolaService;

@WebMvcTest(EscolaController.class)
@Import({ JwtConfig.class, JwtService.class, ApiExceptionHandler.class, EscolaService.class })
class EscolaControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EscolaRepository escolaRepository;

    private String accessToken(String perfil) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA_A);
    }

    private static MockHttpServletRequestBuilder postCom(String token, String json) {
        return post("/escolas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(post("/escolas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Colégio Eduka\"}"))
                .andExpect(status().isUnauthorized());
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void masterNaoCadastraEscola() throws Exception {
        mockMvc.perform(postCom(accessToken("MASTER"), "{\"nome\":\"Colégio Eduka\"}"))
                .andExpect(status().isForbidden());
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void secretariaNaoCadastraEscola() throws Exception {
        mockMvc.perform(postCom(accessToken("SECRETARIA"), "{\"nome\":\"Colégio Eduka\"}"))
                .andExpect(status().isForbidden());
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void superAdminCadastraEscola() throws Exception {
        when(escolaRepository.save(any(Escola.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(postCom(accessToken("SUPER_ADMIN"), """
                {
                  "nome": "Colégio Eduka",
                  "cnpjCpf": "12345678000190",
                  "endereco": { "logradouro": "Rua A", "numero": "10", "cidade": "Belo Horizonte", "estado": "MG" },
                  "email": "contato@eduka.com",
                  "telefone": "3133334444",
                  "plano": "AVANCADO"
                }
                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nome").value("Colégio Eduka"))
                .andExpect(jsonPath("$.cnpjCpf").value("12345678000190"))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua A"))
                .andExpect(jsonPath("$.endereco.numero").value("10"))
                .andExpect(jsonPath("$.endereco.estado").value("MG"))
                .andExpect(jsonPath("$.email").value("contato@eduka.com"))
                .andExpect(jsonPath("$.telefone").value("3133334444"))
                .andExpect(jsonPath("$.plano").value("AVANCADO"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void superAdminSemPlanoResponde400() throws Exception {
        mockMvc.perform(postCom(accessToken("SUPER_ADMIN"), "{\"nome\":\"Colégio Eduka\"}"))
                .andExpect(status().isBadRequest());
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void superAdminComPlanoInexistenteResponde400() throws Exception {
        mockMvc.perform(postCom(accessToken("SUPER_ADMIN"), "{\"nome\":\"Colégio Eduka\",\"plano\":\"OURO\"}"))
                .andExpect(status().isBadRequest());
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void superAdminSemNomeResponde400() throws Exception {
        mockMvc.perform(postCom(accessToken("SUPER_ADMIN"), "{\"nome\":\" \"}"))
                .andExpect(status().isBadRequest());
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void superAdminComCnpjDuplicadoResponde409() throws Exception {
        when(escolaRepository.existsByCnpjCpf("12345678000190")).thenReturn(true);

        mockMvc.perform(postCom(accessToken("SUPER_ADMIN"),
                "{\"nome\":\"Colégio Eduka\",\"cnpjCpf\":\"12345678000190\",\"plano\":\"BASICO\"}"))
                .andExpect(status().isConflict());
        verify(escolaRepository, never()).save(any());
    }
}
