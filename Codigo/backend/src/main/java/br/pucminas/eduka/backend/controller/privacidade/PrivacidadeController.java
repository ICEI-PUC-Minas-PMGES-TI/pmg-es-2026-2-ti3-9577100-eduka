package br.pucminas.eduka.backend.controller.privacidade;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.privacidade.AceitePrivacidadeRequest;
import br.pucminas.eduka.backend.dto.privacidade.AvisoPrivacidadeResponse;
import br.pucminas.eduka.backend.service.privacidade.PrivacidadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Aviso de privacidade e aceite (IU29). Qualquer perfil logado, inclusive o SUPER_ADMIN. */
@RestController
@RequestMapping("/privacidade")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Privacidade", description = "Aviso de privacidade (LGPD) e aceite do usuario logado. Todos os perfis.")
public class PrivacidadeController {

    private final PrivacidadeService privacidadeService;

    public PrivacidadeController(PrivacidadeService privacidadeService) {
        this.privacidadeService = privacidadeService;
    }

    @GetMapping("/aviso")
    @Operation(summary = "O aviso de privacidade em vigor",
            description = "Versao, data, resumo e texto completo, e quando o usuario do token aceitou esta versao "
                    + "(`aceitoEm` nulo: ainda nao aceitou, e a tela deve mostrar o aviso).")
    @ApiResponse(responseCode = "200", description = "O aviso e o aceite do usuario.")
    public AvisoPrivacidadeResponse aviso() {
        return privacidadeService.consultar();
    }

    @PostMapping("/aceite")
    @Operation(summary = "Aceita o aviso de privacidade",
            description = "Grava o aceite da versao em vigor com a data. Repetir o aceite devolve o que ja existe.")
    @ApiResponse(responseCode = "200", description = "O aviso com a data do aceite.")
    @ApiResponse(responseCode = "400", description = "A versao enviada nao e a em vigor: o aviso mudou.")
    public AvisoPrivacidadeResponse aceitar(@Valid @RequestBody AceitePrivacidadeRequest request) {
        return privacidadeService.aceitar(request.versao());
    }
}
