package br.pucminas.eduka.backend.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.dto.RecuperacaoSenhaResponse;
import br.pucminas.eduka.backend.exception.CodigoRecuperacaoInvalidoException;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.service.RecuperacaoSenhaService;

@WebMvcTest(RecuperacaoSenhaController.class)
@Import(JwtConfig.class)
class RecuperacaoSenhaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Test
    void solicitarRespondeAceitoSemToken() throws Exception {
        when(recuperacaoSenhaService.solicitar("ana@escola.com")).thenReturn(new RecuperacaoSenhaResponse(900, 60));

        mockMvc.perform(post("/senha/recuperacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"ana@escola.com"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.validadeSegundos").value(900))
                .andExpect(jsonPath("$.reenvioSegundos").value(60));
    }

    @Test
    void solicitarSemLoginResponde400() throws Exception {
        mockMvc.perform(post("/senha/recuperacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":" "}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recuperacaoSenhaService);
    }

    @Test
    void redefinirComCodigoCertoResponde204() throws Exception {
        mockMvc.perform(post("/senha/redefinicao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"ana@escola.com","codigo":"123456","novaSenha":"senha-nova-123"}
                                """))
                .andExpect(status().isNoContent());
        verify(recuperacaoSenhaService).redefinir("ana@escola.com", "123456", "senha-nova-123");
    }

    @Test
    void codigoInvalidoResponde400ComCodigoDoErro() throws Exception {
        doThrow(new CodigoRecuperacaoInvalidoException()).when(recuperacaoSenhaService)
                .redefinir("ana@escola.com", "123456", "senha-nova-123");

        mockMvc.perform(post("/senha/redefinicao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"ana@escola.com","codigo":"123456","novaSenha":"senha-nova-123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_RECUPERACAO_INVALIDO"));
    }

    @Test
    void codigoForaDoFormatoResponde400() throws Exception {
        mockMvc.perform(post("/senha/redefinicao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"ana@escola.com","codigo":"12ab","novaSenha":"senha-nova-123"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recuperacaoSenhaService);
    }
}
