package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.LogAuditoria;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.LogAuditoriaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.RegistroAtividadesService;

/** Registro de atividades: so o MASTER le, e os parametros da pagina sao validados. */
@WebMvcTest(RegistroAtividadesController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        RegistroAtividadesService.class })
class RegistroAtividadesControllerSecurityTest {

    private static final String PAGINA = "/auditoria/pagina";
    private static final UUID ESCOLA = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private LogAuditoriaRepository logAuditoriaRepository;
    @MockitoBean
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private PessoaRepository pessoaRepository;

    @BeforeEach
    void semAtividades() {
        when(logAuditoriaRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenAnswer(inv -> Page.<LogAuditoria>empty(inv.getArgument(1)));
    }

    @SuppressWarnings("unchecked")
    private static Specification<LogAuditoria> anySpecification() {
        return any(Specification.class);
    }

    private MockHttpServletRequestBuilder como(String perfil, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA));
    }

    @Test
    void masterRecebeAPaginaNoFormatoComum() throws Exception {
        mockMvc.perform(como("MASTER", get(PAGINA).param("tamanho", "5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").isArray())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(5))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void outrosPerfisNaoEntram() throws Exception {
        for (String perfil : new String[] { "FINANCEIRO", "SECRETARIA", "PROFESSOR", "ALUNO", "SUPER_ADMIN" }) {
            mockMvc.perform(como(perfil, get(PAGINA))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PAGINA)).andExpect(status().isUnauthorized());
        verify(logAuditoriaRepository, never()).findAll(anySpecification(), any(Pageable.class));
    }

    @Test
    void parametrosInvalidosRespondem400() throws Exception {
        mockMvc.perform(como("MASTER", get(PAGINA).param("tamanho", "0"))).andExpect(status().isBadRequest());
        mockMvc.perform(como("MASTER", get(PAGINA).param("tamanho", "101"))).andExpect(status().isBadRequest());
        mockMvc.perform(como("MASTER", get(PAGINA).param("pagina", "-1"))).andExpect(status().isBadRequest());
        mockMvc.perform(como("MASTER", get(PAGINA).param("operacao", "APAGAR"))).andExpect(status().isBadRequest());
        mockMvc.perform(como("MASTER", get(PAGINA).param("direcao", "LADO"))).andExpect(status().isBadRequest());
        mockMvc.perform(como("MASTER", get(PAGINA).param("de", "2026-10-31").param("ate", "2026-10-01")))
                .andExpect(status().isBadRequest());
    }
}
