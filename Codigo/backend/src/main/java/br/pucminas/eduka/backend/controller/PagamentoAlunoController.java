package br.pucminas.eduka.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.CobrancaAlunoResponse;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.dto.PagarRequest;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.CobrancaService;
import io.swagger.v3.oas.annotations.Operation;
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
            + "situacaoPagamento (AGUARDANDO_PAGAMENTO, PROCESSANDO, PAGA, CANCELADA) e as acoes da tela. "
            + "Responde 404 se a cobranca nao e do aluno.")
    public CobrancaAlunoResponse buscar(@PathVariable UUID id) {
        return cobrancaService.minhaCobranca(id);
    }

    /** Gera (ou reaproveita) o pagamento da cobranca na forma escolhida. */
    @PostMapping("/{id}/pagar")
    @PreAuthorize(Perfis.ALUNO)
    @Operation(summary = "Pagar cobranca com PIX ou boleto", description = "Perfil: ALUNO, dono da cobranca. "
            + "Devolve o QR Code e o copia e cola (PIX) ou a linha digitavel (boleto). Trocar a forma altera a "
            + "mesma cobranca. 202 com situacao PROCESSANDO quando o provedor ainda nao respondeu; 409 "
            + "COBRANCA_NAO_PAGAVEL, RECEBIMENTOS_INDISPONIVEIS ou PAGAMENTO_EM_PROCESSAMENTO; 400 "
            + "PAGADOR_SEM_CPF ou PROVEDOR_RECUSOU; 404 se a cobranca nao e do aluno.")
    public ResponseEntity<DadosPagamentoResponse> pagar(@PathVariable UUID id,
            @Valid @RequestBody PagarRequest request) {
        DadosPagamentoResponse dados = paymentRoutingService.pagarCobranca(id, request.forma());
        return ResponseEntity.status(dados.pronto() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(dados);
    }
}
