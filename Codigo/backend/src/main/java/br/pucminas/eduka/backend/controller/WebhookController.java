package br.pucminas.eduka.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Avisos do provedor de pagamento. Rota publica: quem chama e o Asaas, que se
 * identifica pelo token do webhook no header {@value #HEADER_TOKEN}, nao por
 * JWT. Todo o resto (conferir o token, nao aplicar o mesmo aviso duas vezes,
 * registrar o pagamento) fica no {@link PaymentRoutingService}.
 */
@RestController
@Tag(name = "Webhooks")
@SecurityRequirements
public class WebhookController {

    static final String HEADER_TOKEN = "Asaas-Access-Token";

    private final PaymentRoutingService paymentRoutingService;

    public WebhookController(PaymentRoutingService paymentRoutingService) {
        this.paymentRoutingService = paymentRoutingService;
    }

    @PostMapping("/webhooks/asaas")
    @Operation(summary = "Recebe avisos do Asaas",
            description = "Chamado pelo Asaas, nao pelo front. Exige o token do webhook no header `"
                    + HEADER_TOKEN + "`. Aviso repetido responde 200 sem efeito; o pagamento confirmado "
                    + "registra o pagamento (origem ONLINE) e marca a cobranca como PAGA. Estorno e contestacao "
                    + "ficam no log de auditoria, sem mudar a cobranca.")
    @ApiResponse(responseCode = "200", description = "Aviso recebido")
    @ApiResponse(responseCode = "400", description = "Corpo ilegivel")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido")
    public ResponseEntity<Void> asaas(@RequestHeader(name = HEADER_TOKEN, required = false) String token,
            @RequestBody String corpo) {
        paymentRoutingService.registrarEvento(token, corpo);
        return ResponseEntity.ok().build();
    }
}
