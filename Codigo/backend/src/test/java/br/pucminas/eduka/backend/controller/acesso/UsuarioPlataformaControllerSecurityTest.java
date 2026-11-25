package br.pucminas.eduka.backend.controller.acesso;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.dto.acesso.UsuarioPlataformaResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.exception.escola.LimiteUsuariosAtingidoException;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.acesso.UsuarioPlataformaService;

/** Contrato de {@code /usuarios/plataforma} (S5-14): so o SUPER_ADMIN entra. */
@WebMvcTest(UsuarioPlataformaController.class)
@Import({ JwtConfig.class, JwtService.class, ApiExceptionHandler.class })
class UsuarioPlataformaControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioPlataformaService service;

    private String bearer(String perfil, UUID escolaId) {
        return "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static UsuarioPlataformaResponse master(UUID id, boolean ativo) {
        return new UsuarioPlataformaResponse(id, "gestor@escola.com", PerfilUsuario.MASTER, ESCOLA, "Colegio",
                ativo, false, Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-01-01T10:00:00Z"), true);
    }

    @Test
    void superAdminListaComFiltrosEPaginacao() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listar(eq(ESCOLA), eq(PerfilUsuario.MASTER), eq(true), eq("gestor"), any(Paginacao.class)))
                .thenReturn(new PaginaResponse<>(List.of(master(id, true)), 0, 20, 1, 1));

        mockMvc.perform(get("/usuarios/plataforma")
                .param("escolaId", ESCOLA.toString()).param("perfil", "MASTER").param("ativo", "true")
                .param("busca", "gestor")
                .header(HttpHeaders.AUTHORIZATION, bearer("SUPER_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].login").value("gestor@escola.com"))
                .andExpect(jsonPath("$.conteudo[0].escolaNome").value("Colegio"))
                .andExpect(jsonPath("$.conteudo[0].gerenciavel").value(true))
                .andExpect(jsonPath("$.conteudo[0].nome").doesNotExist());
    }

    @Test
    void nenhumPerfilDeEscolaEntra() throws Exception {
        for (String perfil : List.of("MASTER", "SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO")) {
            mockMvc.perform(get("/usuarios/plataforma").header(HttpHeaders.AUTHORIZATION, bearer(perfil, ESCOLA)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/usuarios/plataforma/" + UUID.randomUUID() + "/inativar")
                    .header(HttpHeaders.AUTHORIZATION, bearer(perfil, ESCOLA)))
                    .andExpect(status().isForbidden());
        }
        verify(service, never()).listar(any(), any(), any(), any(), any());
        verify(service, never()).inativar(any());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/usuarios/plataforma")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamanhoForaDoLimiteResponde400() throws Exception {
        mockMvc.perform(get("/usuarios/plataforma").param("tamanho", "101")
                .header(HttpHeaders.AUTHORIZATION, bearer("SUPER_ADMIN", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ativarNoLimiteResponde409ComOCodigo() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.ativar(id)).thenThrow(new LimiteUsuariosAtingidoException(50));

        mockMvc.perform(patch("/usuarios/plataforma/" + id + "/ativar")
                .header(HttpHeaders.AUTHORIZATION, bearer("SUPER_ADMIN", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMITE_USUARIOS_ATINGIDO"))
                .andExpect(jsonPath("$.limite").value(50));
    }

    @Test
    void inativarEReenviarDevolvemOLogin() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.inativar(id)).thenReturn(master(id, false));
        when(service.reenviarAcesso(id)).thenReturn(master(id, true));

        mockMvc.perform(patch("/usuarios/plataforma/" + id + "/inativar")
                .header(HttpHeaders.AUTHORIZATION, bearer("SUPER_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(post("/usuarios/plataforma/" + id + "/primeiro-acesso")
                .header(HttpHeaders.AUTHORIZATION, bearer("SUPER_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }
}
