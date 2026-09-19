package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.service.EscolaService;

@WebMvcTest(EscolaController.class)
@Import(JwtConfig.class)
class EscolaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private EscolaService escolaService;

    private EscolaResponse escolaResponse(UUID id, String nome) {
        return new EscolaResponse(id, nome, "12.345.678/0001-90", "Rua das Escolas, 100",
                "contato@escola.com", "(31) 4000-0000", true, Instant.now());
    }

    private String tokenComPerfil(String perfil) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(60, ChronoUnit.MINUTES))
                .claim("perfil", perfil)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void retornaListaDeEscolas() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.listar()).thenReturn(List.of(escolaResponse(id, "Escola A")));

        mockMvc.perform(get("/escolas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].nome").value("Escola A"));
    }

    @Test
    void retornaListaVaziaQuandoNaoHaEscolas() throws Exception {
        when(escolaService.listar()).thenReturn(List.of());

        mockMvc.perform(get("/escolas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void retornaDetalheDeUmaEscola() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.buscarPorId(id)).thenReturn(escolaResponse(id, "Escola A"));

        mockMvc.perform(get("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Escola A"));
    }

    @Test
    void retorna404ParaEscolaInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.buscarPorId(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "escola não encontrada"));

        mockMvc.perform(get("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna401AoListarSemToken() throws Exception {
        mockMvc.perform(get("/escolas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retorna403AoListarComPerfilNaoAutorizado() throws Exception {
        mockMvc.perform(get("/escolas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("PROFESSOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void retorna403AoBuscarDetalheComPerfilNaoAutorizado() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("PROFESSOR")))
                .andExpect(status().isForbidden());
    }

    private static final String ESCOLA_UPDATE_JSON = """
            {
                "nome": "Escola A Renomeada",
                "cnpjCpf": "98.765.432/0001-10",
                "endereco": "Av. Nova, 200",
                "email": "novo@escola.com",
                "telefone": "(31) 5000-0000"
            }
            """;

    @Test
    void atualizaDadosDeUmaEscola() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.atualizar(eq(id), any())).thenReturn(escolaResponse(id, "Escola A Renomeada"));

        mockMvc.perform(put("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ESCOLA_UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Escola A Renomeada"));
    }

    @Test
    void retorna404AoAtualizarEscolaInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.atualizar(eq(id), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "escola não encontrada"));

        mockMvc.perform(put("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ESCOLA_UPDATE_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoAtualizacaoViolaIntegridadeDoBanco() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.atualizar(eq(id), any()))
                .thenThrow(new DataIntegrityViolationException("valor excede o tamanho da coluna"));

        mockMvc.perform(put("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ESCOLA_UPDATE_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400AoAtualizarComNomeEmBranco() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nome": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna401AoAtualizarSemToken() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/escolas/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ESCOLA_UPDATE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retorna403AoAtualizarComPerfilNaoAutorizado() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/escolas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("PROFESSOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ESCOLA_UPDATE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void ativaUmaEscola() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.ativar(id)).thenReturn(escolaResponse(id, "Escola A"));

        mockMvc.perform(patch("/escolas/{id}/ativar", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void retorna404AoAtivarEscolaInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.ativar(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "escola não encontrada"));

        mockMvc.perform(patch("/escolas/{id}/ativar", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna403AoAtivarComPerfilNaoAutorizado() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/escolas/{id}/ativar", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("PROFESSOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendeUmaEscola() throws Exception {
        UUID id = UUID.randomUUID();
        EscolaResponse suspensa = new EscolaResponse(id, "Escola A", "12.345.678/0001-90", "Rua das Escolas, 100",
                "contato@escola.com", "(31) 4000-0000", false, Instant.now());
        when(escolaService.suspender(id)).thenReturn(suspensa);

        mockMvc.perform(patch("/escolas/{id}/suspender", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void retorna404AoSuspenderEscolaInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(escolaService.suspender(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "escola não encontrada"));

        mockMvc.perform(patch("/escolas/{id}/suspender", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("SUPER_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna403AoSuspenderComPerfilNaoAutorizado() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/escolas/{id}/suspender", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComPerfil("PROFESSOR")))
                .andExpect(status().isForbidden());
    }
}
