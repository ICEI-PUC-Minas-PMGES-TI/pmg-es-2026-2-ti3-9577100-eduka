package br.pucminas.eduka.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;

/**
 * Cadastra, na subida da aplicacao, o webhook da conta-pai do Eduka, que
 * recebe as faturas da plataforma (Fluxo A). So roda com
 * {@code asaas.webhook-conta-pai} ligado ({@code ASAAS_WEBHOOK_CONTA_PAI}),
 * que deve ficar so na prod: o Sandbox e compartilhado pela equipe, e um
 * webhook de tunel temporario morre depois.
 *
 * <p>
 * Falha no Asaas nao derruba a aplicacao: fica o aviso no log, e a
 * reconciliacao continua cobrindo os pagamentos das faturas.
 * </p>
 */
@Component
public class WebhookContaPaiRotina {

    private static final Logger log = LoggerFactory.getLogger(WebhookContaPaiRotina.class);

    private final PaymentRoutingService paymentRoutingService;
    private final boolean ligada;

    public WebhookContaPaiRotina(PaymentRoutingService paymentRoutingService,
            @Value("${asaas.webhook-conta-pai:false}") boolean ligada) {
        this.paymentRoutingService = paymentRoutingService;
        this.ligada = ligada;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void garantirWebhook() {
        if (!ligada) {
            return;
        }
        try {
            paymentRoutingService.garantirWebhookDaContaPai();
        } catch (RuntimeException e) {
            log.warn("Webhook da conta-pai nao cadastrado; a reconciliacao cobre as faturas: {}", e.getMessage());
        }
    }
}
