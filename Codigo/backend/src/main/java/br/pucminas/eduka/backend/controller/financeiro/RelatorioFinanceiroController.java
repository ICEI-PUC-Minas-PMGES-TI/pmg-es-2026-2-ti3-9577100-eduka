package br.pucminas.eduka.backend.controller.financeiro;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.comum.ArquivoCsv;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.financeiro.RelatorioFinanceiroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Relatorios financeiros por periodo (RF19). A escola vem do token. */
@RestController
@RequestMapping("/relatorios/financeiro")
@Tag(name = "Relatorios", description = "Relatorios financeiros da escola. Todas as operacoes: MASTER, FINANCEIRO.")
public class RelatorioFinanceiroController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final RelatorioFinanceiroService relatorioFinanceiroService;

    public RelatorioFinanceiroController(RelatorioFinanceiroService relatorioFinanceiroService) {
        this.relatorioFinanceiroService = relatorioFinanceiroService;
    }

    @GetMapping
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Relatorio financeiro do periodo",
            description = "De `de` a `ate` (AAAA-MM-DD, inclusivos, no maximo 24 meses): recebido, estornado, "
                    + "despesas lancadas e pagas, saldo, inadimplencia, receitas e despesas por mes, formas de "
                    + "pagamento e o resumo por curso, tipo e mes. Recebido (ja sem os estornos do periodo), despesas "
                    + "pagas e saldo contam pelo dia em que o dinheiro se moveu; cobrancas e despesas lancadas, pelo "
                    + "vencimento, sem as canceladas. Perfis: MASTER, FINANCEIRO.")
    @ApiResponse(responseCode = "200", description = "O relatorio; periodo sem movimento vem com tudo zerado.")
    @ApiResponse(responseCode = "400", description = "Periodo invertido, com mais de 24 meses ou data invalida.")
    public RelatorioFinanceiroResponse gerar(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate ate) {
        return relatorioFinanceiroService.gerar(de, ate);
    }

    @GetMapping("/exportar")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Exportar o relatorio em CSV",
            description = "Um arquivo so, com uma secao para cada conjunto em `conjuntos` (uma linha por cobranca "
                    + "que vence no periodo, por pagamento ou estorno, este com valor negativo, ou por despesa que "
                    + "vence nele, sem as canceladas). UTF-8 com BOM; com `PONTO_E_VIRGULA`, o padrao, os valores usam virgula decimal. "
                    + "O nome do arquivo traz o periodo. Vem como anexo, sem cache. Perfis: MASTER, FINANCEIRO.")
    @ApiResponse(responseCode = "200", description = "O arquivo CSV.",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = "Nenhum conjunto, conjunto ou separador desconhecido, periodo "
            + "invertido, com mais de 24 meses ou data invalida.")
    public ResponseEntity<byte[]> exportar(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate ate,
            @RequestParam Set<ArquivoCsv.Conjunto> conjuntos,
            @RequestParam(defaultValue = "PONTO_E_VIRGULA") ArquivoCsv.Separador separador) {
        ArquivoCsv arquivo = relatorioFinanceiroService.exportar(de, ate, conjuntos, separador);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                // Traz nomes de alunos e valores: nao fica em cache compartilhado.
                .cacheControl(CacheControl.noStore())
                .body(arquivo.conteudo());
    }
}
