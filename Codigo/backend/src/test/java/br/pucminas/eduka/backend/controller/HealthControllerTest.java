package br.pucminas.eduka.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;

/**
 * GET /health com o banco no ar (200) e fora (503): o corpo traz so o estado,
 * nunca a mensagem da excecao (a rota e publica).
 */
@WebMvcTest(HealthController.class)
@Import({ JwtConfig.class, JwtService.class })
@TestPropertySource(properties = "eduka.health.timeout-ms=300")
class HealthControllerTest {

    private static final String SEGREDO = "jdbc:postgresql://db-interno:5432/eduka user=eduka_app senha=hunter2";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataSource dataSource;

    private final Connection conexao = org.mockito.Mockito.mock(Connection.class);
    private final Statement consulta = org.mockito.Mockito.mock(Statement.class);

    @BeforeEach
    void prepararBanco() throws SQLException {
        when(dataSource.getConnection()).thenReturn(conexao);
        when(conexao.createStatement()).thenReturn(consulta);
    }

    @Test
    void bancoNoArResponde200ComStatusUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.banco").value("UP"))
                .andExpect(content().json("{\"status\":\"UP\",\"banco\":\"UP\"}", true));

        verify(consulta).setQueryTimeout(anyInt());
        verify(consulta).execute("SELECT 1");
        verify(conexao).close();
    }

    @Test
    void semConexaoComOBancoResponde503SemDetalhe() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException(SEGREDO));

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.banco").value("DOWN"))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("SQLException"))));
    }

    @Test
    void consultaQueFalhaResponde503SemDetalhe() throws Exception {
        doThrow(new SQLException(SEGREDO)).when(consulta).execute("SELECT 1");

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(content().string(not(containsString("hunter2"))));

        verify(conexao).close();
    }

    @Test
    void bancoQueNaoRespondeNoPrazoResponde503() throws Exception {
        when(dataSource.getConnection()).thenAnswer(invocacao -> {
            Thread.sleep(5_000);
            return conexao;
        });

        long inicio = System.nanoTime();
        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));

        long decorridoMs = (System.nanoTime() - inicio) / 1_000_000;
        org.junit.jupiter.api.Assertions.assertTrue(decorridoMs < 2_000,
                "o /health deve responder perto do timeout, nao esperar o banco: " + decorridoMs + " ms");
    }
}
