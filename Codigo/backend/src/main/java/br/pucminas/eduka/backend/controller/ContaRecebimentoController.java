package br.pucminas.eduka.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.AtivarRecebimentosRequest;
import br.pucminas.eduka.backend.dto.ContaRecebimentoResponse;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.security.Perfis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Conta de recebimento da escola (spec §8.1): o MASTER ativa a conta filha no
 * provedor e acompanha a aprovacao. Sempre a escola do token.
 */
@RestController
@Tag(name = "Conta de recebimento", description = "Conta filha da escola no provedor de pagamento, para receber "
        + "as cobrancas dos alunos. Perfil: MASTER.")
public class ContaRecebimentoController {

    private final PaymentRoutingService paymentRoutingService;

    public ContaRecebimentoController(PaymentRoutingService paymentRoutingService) {
        this.paymentRoutingService = paymentRoutingService;
    }

    @GetMapping("/config-pagamento/conta")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Conta de recebimento da minha escola", description = "Perfil: MASTER. Status "
            + "NAO_INICIADA enquanto nada foi enviado. Nunca traz a chave da conta nem os dados bancarios: so o "
            + "codigo do banco e os 4 ultimos digitos da conta.")
    public ContaRecebimentoResponse consultar() {
        return paymentRoutingService.consultarConta();
    }

    @PostMapping("/config-pagamento/conta/ativar")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Ativar recebimentos", description = "Perfil: MASTER. Cria a conta filha da escola com o "
            + "CNPJ do cadastro dela. Permitido em NAO_INICIADA, ERRO_CRIACAO e VERIFICACAO_PENDENTE. Devolve a "
            + "conta em AGUARDANDO_APROVACAO (ou APROVADA), ou VERIFICACAO_PENDENTE se o provedor nao respondeu. "
            + "O pedido aceito fica no log de auditoria com o MASTER. 409 ATIVACAO_EM_ANDAMENTO ou "
            + "CONTA_JA_CRIADA; 400 PROVEDOR_RECUSOU (a conta fica ERRO_CRIACAO); 503 PROVEDOR_NAO_CONFIGURADO.")
    public ContaRecebimentoResponse ativar(@Valid @RequestBody AtivarRecebimentosRequest request) {
        return paymentRoutingService.ativarRecebimentos(request);
    }

    @PostMapping("/config-pagamento/conta/sincronizar")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Sincronizar a conta", description = "Perfil: MASTER. Le a situacao da conta no "
            + "provedor e grava; mudanca de situacao fica no log de auditoria, sem autor. Conta ainda nao criada "
            + "no provedor volta como esta. 404 sem conta; 503 PROVEDOR_INDISPONIVEL.")
    public ContaRecebimentoResponse sincronizar() {
        return paymentRoutingService.sincronizarConta();
    }
}
