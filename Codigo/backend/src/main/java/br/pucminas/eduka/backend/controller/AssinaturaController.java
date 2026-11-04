package br.pucminas.eduka.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.AssinaturaResponse;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.EscolaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Assinatura da escola do token, para a tela Plano e faturas do MASTER (IU24-A). */
@RestController
@Tag(name = "Assinatura", description = "Plano, situacao e vigencia da assinatura da escola. Perfil: MASTER.")
public class AssinaturaController {

    private final EscolaService escolaService;
    private final IsolamentoEscola isolamentoEscola;

    public AssinaturaController(EscolaService escolaService, IsolamentoEscola isolamentoEscola) {
        this.escolaService = escolaService;
        this.isolamentoEscola = isolamentoEscola;
    }

    @GetMapping("/config-pagamento/assinatura")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Assinatura da minha escola", description = "Perfil: MASTER. Sempre a escola do token.")
    public AssinaturaResponse minhaAssinatura() {
        return escolaService.assinatura(isolamentoEscola.escolaIdAtual());
    }
}
