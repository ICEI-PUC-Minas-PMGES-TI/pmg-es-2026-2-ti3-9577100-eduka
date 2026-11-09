package br.pucminas.eduka.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.exception.RegistroEmUsoException;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.DadosPessoaisAlunoService;

/**
 * Quem exporta e quem anonimiza: a secretaria entrega o relatorio ao titular,
 * mas so o MASTER apaga o dado pessoal, que nao tem volta.
 */
@WebMvcTest(DadosPessoaisAlunoController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class })
class DadosPessoaisAlunoControllerSecurityTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID ALUNO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private DadosPessoaisAlunoService service;

    private String token(String perfil) {
        return "Bearer " + jwtService.generateAccessToken(UUID.randomUUID(), perfil, ESCOLA);
    }

    @Test
    void secretariaBaixaOPdfComoAnexoSemCache() throws Exception {
        when(service.exportar(ALUNO)).thenReturn(
                new DadosPessoaisAlunoService.Arquivo("dados-do-aluno-ana-2026-11-08.pdf", "%PDF-1.4".getBytes()));

        mockMvc.perform(get("/alunos/{id}/dados-pessoais", ALUNO)
                .header(HttpHeaders.AUTHORIZATION, token("SECRETARIA")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dados-do-aluno-ana-2026-11-08.pdf\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void financeiroEProfessorNaoExportam() throws Exception {
        for (String perfil : new String[] { "FINANCEIRO", "PROFESSOR" }) {
            mockMvc.perform(get("/alunos/{id}/dados-pessoais", ALUNO)
                    .header(HttpHeaders.AUTHORIZATION, token(perfil)))
                    .andExpect(status().isForbidden());
        }
        verify(service, never()).exportar(any());
    }

    @Test
    void soOMasterAnonimiza() throws Exception {
        mockMvc.perform(post("/alunos/{id}/anonimizar", ALUNO).header(HttpHeaders.AUTHORIZATION, token("SECRETARIA")))
                .andExpect(status().isForbidden());
        verify(service, never()).anonimizar(any());

        mockMvc.perform(post("/alunos/{id}/anonimizar", ALUNO).header(HttpHeaders.AUTHORIZATION, token("MASTER")))
                .andExpect(status().isNoContent());
        verify(service).anonimizar(ALUNO);
    }

    @Test
    void alunoQueAindaEstudaVoltaComConflito() throws Exception {
        doThrow(new RegistroEmUsoException("Aluno com matricula ativa ou trancada"))
                .when(service).anonimizar(ALUNO);

        mockMvc.perform(post("/alunos/{id}/anonimizar", ALUNO).header(HttpHeaders.AUTHORIZATION, token("MASTER")))
                .andExpect(status().isConflict());
    }
}
