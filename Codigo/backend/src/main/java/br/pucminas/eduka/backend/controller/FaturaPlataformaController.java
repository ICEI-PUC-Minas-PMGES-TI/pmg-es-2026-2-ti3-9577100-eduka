package br.pucminas.eduka.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.FaturaPlataformaResponse;
import br.pucminas.eduka.backend.dto.LancarFaturaRequest;
import br.pucminas.eduka.backend.dto.PagarRequest;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.security.Perfis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Fatura da plataforma (Fluxo A, spec §10.1 F1 a F7): o Super Admin lanca,
 * lista e cancela as faturas de uma escola em {@code /escolas/{escolaId}/faturas};
 * o MASTER ve e paga as da propria escola em {@code /config-pagamento/faturas}.
 */
@RestController
@Tag(name = "Faturas da plataforma", description = "O que a escola paga ao Eduka. Lancar e cancelar: SUPER_ADMIN. "
        + "Ver e pagar: MASTER da escola.")
public class FaturaPlataformaController {

    private final PaymentRoutingService paymentRoutingService;

    public FaturaPlataformaController(PaymentRoutingService paymentRoutingService) {
        this.paymentRoutingService = paymentRoutingService;
    }

    // ---- Super Admin ----

    @PostMapping("/escolas/{escolaId}/faturas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Perfis.SUPER_ADMIN)
    @Operation(summary = "Lancar fatura", description = "Perfil: SUPER_ADMIN. Escola ativa, competencia yyyy-MM "
            + "unica por escola (fora as canceladas), valor > 0 e vencimento a partir de hoje. 409 "
            + "FATURA_DUPLICADA; 400 dados invalidos ou escola suspensa; 404 escola inexistente.")
    public FaturaPlataformaResponse lancar(@PathVariable UUID escolaId,
            @Valid @RequestBody LancarFaturaRequest request) {
        return paymentRoutingService.lancarFatura(escolaId, request);
    }

    @GetMapping("/escolas/{escolaId}/faturas")
    @PreAuthorize(Perfis.SUPER_ADMIN)
    @Operation(summary = "Faturas da escola", description = "Perfil: SUPER_ADMIN. Da competencia mais recente "
            + "para a mais antiga, com as acoes (CANCELAR, VER_COMPROVANTE). 404 escola inexistente.")
    public List<FaturaPlataformaResponse> listar(@PathVariable UUID escolaId) {
        return paymentRoutingService.listarFaturas(escolaId);
    }

    @PatchMapping("/escolas/{escolaId}/faturas/{id}/cancelar")
    @PreAuthorize(Perfis.SUPER_ADMIN)
    @Operation(summary = "Cancelar fatura", description = "Perfil: SUPER_ADMIN. Ja emitida, remove antes no "
            + "provedor. 409 COBRANCA_JA_PAGA (paga aqui ou no provedor, e entao registrada) ou "
            + "PAGAMENTO_EM_PROCESSAMENTO; 503 PROVEDOR_INDISPONIVEL.")
    public FaturaPlataformaResponse cancelar(@PathVariable UUID escolaId, @PathVariable UUID id) {
        return paymentRoutingService.cancelarFatura(escolaId, id);
    }

    @GetMapping("/escolas/{escolaId}/faturas/{id}/comprovante")
    @PreAuthorize(Perfis.SUPER_ADMIN)
    @Operation(summary = "Comprovante da fatura", description = "Perfil: SUPER_ADMIN. 404 "
            + "COMPROVANTE_INDISPONIVEL enquanto a fatura nao foi paga.")
    public ComprovanteResponse comprovante(@PathVariable UUID escolaId, @PathVariable UUID id) {
        return paymentRoutingService.comprovanteFatura(escolaId, id);
    }

    // ---- MASTER da escola do token ----

    @GetMapping("/config-pagamento/faturas")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Faturas da minha escola", description = "Perfil: MASTER. So as da escola do token, com "
            + "as acoes (PAGAR, VER_COMPROVANTE).")
    public List<FaturaPlataformaResponse> minhas() {
        return paymentRoutingService.listarMinhasFaturas();
    }

    @PostMapping("/config-pagamento/faturas/{id}/pagar")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Pagar fatura com PIX ou boleto", description = "Perfil: MASTER da escola da fatura. "
            + "Devolve o QR Code e o copia e cola (PIX) ou a linha digitavel (boleto); trocar a forma altera a "
            + "mesma cobranca. 202 com situacao PROCESSANDO quando o provedor ainda nao respondeu; 409 "
            + "COBRANCA_NAO_PAGAVEL ou PAGAMENTO_EM_PROCESSAMENTO; 400 PROVEDOR_RECUSOU; 404 fatura de outra "
            + "escola.")
    public ResponseEntity<DadosPagamentoResponse> pagar(@PathVariable UUID id,
            @Valid @RequestBody PagarRequest request) {
        DadosPagamentoResponse dados = paymentRoutingService.pagarFatura(id, request.forma());
        return ResponseEntity.status(dados.pronto() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(dados);
    }

    @GetMapping("/config-pagamento/faturas/{id}/comprovante")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Comprovante da minha fatura", description = "Perfil: MASTER da escola da fatura. 404 "
            + "COMPROVANTE_INDISPONIVEL enquanto a fatura nao foi paga.")
    public ComprovanteResponse meuComprovante(@PathVariable UUID id) {
        return paymentRoutingService.comprovanteMinhaFatura(id);
    }
}
