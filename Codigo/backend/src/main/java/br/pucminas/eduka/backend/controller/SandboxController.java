package br.pucminas.eduka.backend.controller;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.security.Perfis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Atalhos de teste no Sandbox do provedor (NOVA-6). So existem nos perfis dev
 * e demo (ambiente de apresentacao): fora deles as rotas respondem 404.
 */
@RestController
@Profile({ "dev", "demo" })
@Tag(name = "Sandbox", description = "Atalhos de teste do provedor de pagamento, so em desenvolvimento. "
        + "Perfis: MASTER (conta), FINANCEIRO e ALUNO (cobranca).")
public class SandboxController {

    private final PaymentRoutingService paymentRoutingService;

    public SandboxController(PaymentRoutingService paymentRoutingService) {
        this.paymentRoutingService = paymentRoutingService;
    }

    @PostMapping("/config-pagamento/conta/sandbox/aprovar")
    @PreAuthorize(Perfis.GESTAO)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Aprovar a conta no Sandbox", description = "Perfil: MASTER. Aprova no Sandbox a conta "
            + "filha da minha escola; depois use Sincronizar ou espere o aviso do provedor. 404 sem conta; 400 "
            + "conta ainda nao criada no provedor; 409 SANDBOX_INDISPONIVEL fora do Sandbox.")
    public void aprovarConta() {
        paymentRoutingService.sandboxAprovarConta();
    }

    @PostMapping("/cobrancas/{id}/sandbox/confirmar-pagamento")
    @PreAuthorize(Perfis.FINANCEIRO)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Simular o pagamento no Sandbox", description = "Perfil: FINANCEIRO. Confirma no Sandbox "
            + "o pagamento da cobranca ja emitida; o aviso do provedor marca a cobranca como paga. 404 cobranca de "
            + "outra escola; 400 cobranca ainda nao emitida; 409 SANDBOX_INDISPONIVEL fora do Sandbox.")
    public void confirmarPagamento(@PathVariable UUID id) {
        paymentRoutingService.sandboxConfirmarPagamento(id);
    }

    @PostMapping("/portal/aluno/cobrancas/{id}/sandbox/pagar")
    @PreAuthorize(Perfis.ALUNO)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pagar a propria cobranca no Sandbox", description = "Perfil: ALUNO, dono da cobranca. "
            + "Depois de Pagar com PIX ou boleto, confirma o pagamento no Sandbox como se o banco tivesse pago; a "
            + "cobranca fica paga sem esperar o aviso do provedor. 404 cobranca de outro aluno; 400 cobranca "
            + "ainda nao emitida; 409 SANDBOX_INDISPONIVEL fora do Sandbox.")
    public void pagarComoAluno(@PathVariable UUID id) {
        paymentRoutingService.sandboxPagarComoAluno(id);
    }
}
