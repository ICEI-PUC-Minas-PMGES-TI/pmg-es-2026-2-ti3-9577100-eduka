package br.pucminas.eduka.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.SuspensaoPrevistaResponse;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.SuspensaoPorAtrasoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Aviso de suspensao da escola do token, para o MASTER ver em todas as telas. */
@RestController
@Tag(name = "Assinatura", description = "Plano, situacao e vigencia da assinatura da escola. Perfil: MASTER.")
public class SuspensaoPrevistaController {

    private final SuspensaoPorAtrasoService suspensaoPorAtrasoService;
    private final IsolamentoEscola isolamentoEscola;

    public SuspensaoPrevistaController(SuspensaoPorAtrasoService suspensaoPorAtrasoService,
            IsolamentoEscola isolamentoEscola) {
        this.suspensaoPorAtrasoService = suspensaoPorAtrasoService;
        this.isolamentoEscola = isolamentoEscola;
    }

    @GetMapping("/config-pagamento/assinatura/suspensao")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Suspensao prevista da minha escola",
            description = "Quando uma fatura do Eduka em atraso passou da tolerancia: o dia em que a escola sera "
                    + "suspensa se ela continuar em aberto, e a fatura. Perfil: MASTER.")
    @ApiResponse(responseCode = "200", description = "Ha suspensao prevista.")
    @ApiResponse(responseCode = "204", description = "Nenhuma suspensao prevista.")
    public ResponseEntity<SuspensaoPrevistaResponse> prevista() {
        return suspensaoPorAtrasoService.prevista(isolamentoEscola.escolaIdAtual())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
