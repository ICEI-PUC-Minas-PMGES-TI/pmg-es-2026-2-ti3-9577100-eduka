package br.pucminas.eduka.backend.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.CobrancaAlunoResponse;
import br.pucminas.eduka.backend.dto.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.PagarRequest;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.CobrancaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Cobrancas do aluno autenticado, para pagar sem sair do Eduka. O aluno e a
 * escola saem do token; as regras ficam no {@link PaymentRoutingService}.
 */
@RestController
@RequestMapping("/portal/aluno/cobrancas")
@Tag(name = "Pagamento do aluno", description = "Pagar as proprias cobrancas com PIX ou boleto. Perfil: ALUNO.")
public class PagamentoAlunoController {

    private final PaymentRoutingService paymentRoutingService;
    private final CobrancaService cobrancaService;

    public PagamentoAlunoController(PaymentRoutingService paymentRoutingService, CobrancaService cobrancaService) {
        this.paymentRoutingService = paymentRoutingService;
        this.cobrancaService = cobrancaService;
    }

    /** Uma cobranca do aluno, com a situacao do pagamento. A tela consulta enquanto espera a confirmacao. */
    @GetMapping("/{id}")
    @PreAuthorize(Perfis.ALUNO)
    @Operation(summary = "Cobranca do aluno autenticado", description = "Perfil: ALUNO, dono da cobranca. Traz "
            + "situacaoPagamento (AGUARDANDO_PAGAMENTO, PROCESSANDO, PAGA, CANCELADA) e as acoes da tela.")
    @ApiResponse(responseCode = "200", description = "A cobranca, com a situacao do pagamento.")
    @ApiResponse(responseCode = "404", description = "Cobranca que nao e do aluno.")
    public CobrancaAlunoResponse buscar(@PathVariable UUID id) {
        return cobrancaService.minhaCobranca(id);
    }

    /**
     * PDF oficial do boleto, buscado no provedor na hora. O link do provedor
     * nunca chega ao navegador.
     */
    @GetMapping("/{id}/boleto")
    @PreAuthorize(Perfis.ALUNO)
    @Operation(summary = "Baixar o PDF do boleto", description = "Perfil: ALUNO, dono da cobranca. So depois de "
            + "Pagar com BOLETO. Vem como anexo, sem cache.")
    @ApiResponse(responseCode = "200", description = "PDF oficial do boleto.",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Cobranca que nao e do aluno.")
    @ApiResponse(responseCode = "409", description = "`BOLETO_NAO_GERADO` se ainda nao pagou com boleto; "
            + "`COBRANCA_NAO_PAGAVEL` se a cobranca foi cancelada.")
    @ApiResponse(responseCode = "503", description = "`PROVEDOR_INDISPONIVEL`: o Asaas nao respondeu.")
    public ResponseEntity<byte[]> baixarBoleto(@PathVariable UUID id) {
        byte[] pdf = paymentRoutingService.baixarBoleto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("boleto-" + id + ".pdf").build().toString())
                // Documento de pagamento: nao fica em cache compartilhado.
                .cacheControl(CacheControl.noStore())
                .body(pdf);
    }

    /** Comprovante montado pelo Eduka; o link do recibo do provedor nunca sai do backend. */
    @GetMapping("/{id}/comprovante")
    @PreAuthorize(Perfis.ALUNO)
    @Operation(summary = "Comprovante do pagamento", description = "Perfil: ALUNO, dono da cobranca. Montado "
            + "pelo Eduka depois que o Asaas confirma o pagamento.")
    @ApiResponse(responseCode = "200", description = "O comprovante.")
    @ApiResponse(responseCode = "404", description = "`COMPROVANTE_INDISPONIVEL` enquanto o pagamento nao foi "
            + "confirmado; sem codigo se a cobranca nao e do aluno.")
    public ComprovanteResponse comprovante(@PathVariable UUID id) {
        return paymentRoutingService.comprovanteCobranca(id);
    }

    /** Gera (ou reaproveita) o pagamento da cobranca na forma escolhida. */
    @PostMapping("/{id}/pagar")
    @PreAuthorize(Perfis.ALUNO)
    @Operation(summary = "Pagar cobranca com PIX ou boleto", description = "Perfil: ALUNO, dono da cobranca. "
            + "Devolve o QR Code e o copia e cola (PIX) ou a linha digitavel (boleto). Trocar a forma altera a "
            + "mesma cobranca.")
    @ApiResponse(responseCode = "200", description = "Situacao PRONTO, com `pix` ou `boleto` preenchido.")
    @ApiResponse(responseCode = "202", description = "Situacao PROCESSANDO: o Asaas ainda nao respondeu. Consulte "
            + "`GET /portal/aluno/cobrancas/{id}` ate a situacao mudar.",
            content = @Content(schema = @Schema(implementation = DadosPagamentoResponse.class)))
    @ApiResponse(responseCode = "400", description = "`FORMA_INVALIDA`, `PAGADOR_SEM_CPF` ou `PROVEDOR_RECUSOU`.")
    @ApiResponse(responseCode = "404", description = "Cobranca que nao e do aluno.")
    @ApiResponse(responseCode = "409", description = "`COBRANCA_NAO_PAGAVEL` (paga ou cancelada), "
            + "`RECEBIMENTOS_INDISPONIVEIS` (escola sem conta aprovada) ou `PAGAMENTO_EM_PROCESSAMENTO` (outro "
            + "clique esta gerando o pagamento).")
    @ApiResponse(responseCode = "503", description = "`PROVEDOR_INDISPONIVEL` ou `PROVEDOR_NAO_CONFIGURADO`; pode "
            + "tentar de novo.")
    public ResponseEntity<DadosPagamentoResponse> pagar(@PathVariable UUID id,
            @Valid @RequestBody PagarRequest request) {
        DadosPagamentoResponse dados = paymentRoutingService.pagarCobranca(id, request.forma());
        return ResponseEntity.status(dados.pronto() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(dados);
    }
}
