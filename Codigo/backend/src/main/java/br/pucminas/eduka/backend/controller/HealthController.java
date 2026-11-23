package br.pucminas.eduka.backend.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.comum.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;

@RestController
@Tag(name = "Health")
@SecurityRequirements
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final long timeoutMs;
    // A consulta roda fora da thread da requisicao: com o banco fora, pegar uma conexao do pool
    // pode esperar o connection-timeout do Hikari (30 s), e o /health tem de responder antes.
    private final ExecutorService executor = Executors.newCachedThreadPool(tarefa -> {
        Thread thread = new Thread(tarefa, "health-banco");
        thread.setDaemon(true);
        return thread;
    });

    public HealthController(DataSource dataSource,
            @Value("${eduka.health.timeout-ms:3000}") long timeoutMs) {
        this.dataSource = dataSource;
        this.timeoutMs = timeoutMs;
    }

    @GetMapping("/health")
    @Operation(summary = "Verifica se a API e o banco estao no ar",
            description = "Faz uma consulta leve (`SELECT 1`) com timeout curto. Rota publica; o corpo traz so "
                    + "`status` e `banco` (UP ou DOWN), sem detalhe interno.")
    @ApiResponse(responseCode = "200", description = "API e banco no ar: `{\"status\":\"UP\",\"banco\":\"UP\"}`.")
    @ApiResponse(responseCode = "503", description = "Banco fora ou lento: `{\"status\":\"DOWN\",\"banco\":\"DOWN\"}`.")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse corpo = HealthResponse.de(bancoResponde());
        return ResponseEntity.status(corpo.noAr() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(corpo);
    }

    private boolean bancoResponde() {
        Future<Boolean> consulta = executor.submit(this::consultarBanco);
        try {
            return consulta.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            consulta.cancel(true);
            return false;
        } catch (Exception e) {
            consulta.cancel(true);
            // Apenas a classe da falha: a mensagem pode trazer host, usuario ou trecho de SQL.
            log.warn("Health check: banco indisponivel ({})", causa(e));
            return false;
        }
    }

    private boolean consultarBanco() throws SQLException {
        try (Connection conexao = dataSource.getConnection(); Statement consulta = conexao.createStatement()) {
            consulta.setQueryTimeout((int) Math.max(1, TimeUnit.MILLISECONDS.toSeconds(timeoutMs)));
            consulta.execute("SELECT 1");
            return true;
        }
    }

    private static String causa(Exception e) {
        Throwable causa = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null
                ? e.getCause()
                : e;
        return causa.getClass().getSimpleName();
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

}
