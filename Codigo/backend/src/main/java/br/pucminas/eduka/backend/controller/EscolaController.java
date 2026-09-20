package br.pucminas.eduka.backend.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domains.model.Escola;
import br.pucminas.eduka.backend.dto.EscolaRequest;
import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.service.EscolaService;

/**
 * Cadastro de escolas contratantes. Escrita restrita a {@code SUPER_ADMIN}
 * (RF06): criar um tenant e operacao de plataforma, nao de uma escola.
 */
@RestController
@RequestMapping("/escolas")
public class EscolaController {

    private final EscolaService escolaService;

    public EscolaController(EscolaService escolaService) {
        this.escolaService = escolaService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<EscolaResponse> cadastrar(@RequestBody EscolaRequest request) {
        Escola escola = escolaService.cadastrar(request);
        return ResponseEntity.created(URI.create("/escolas/" + escola.getId()))
                .body(EscolaResponse.de(escola));
    }
}
