package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.dto.EscolaRequest;
import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.dto.EscolaUpdateRequest;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.EscolaService;
import jakarta.validation.Valid;

/**
 * Cadastro e administracao de escolas contratantes. Restrito a
 * {@code SUPER_ADMIN} (RF06): escola e o tenant do sistema, nao um dado de
 * negocio de uma escola.
 */
@RestController
@RequestMapping("/escolas")
@PreAuthorize(Perfis.SUPER_ADMIN)
public class EscolaController {

    private final EscolaService escolaService;

    public EscolaController(EscolaService escolaService) {
        this.escolaService = escolaService;
    }

    @GetMapping
    public List<EscolaResponse> listar() {
        return escolaService.listar();
    }

    @GetMapping("/{id}")
    public EscolaResponse buscarPorId(@PathVariable UUID id) {
        return escolaService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<EscolaResponse> cadastrar(@Valid @RequestBody EscolaRequest request) {
        Escola escola = escolaService.cadastrar(request);
        return ResponseEntity.created(URI.create("/escolas/" + escola.getId()))
                .body(EscolaResponse.from(escola));
    }

    @PutMapping("/{id}")
    public EscolaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody EscolaUpdateRequest request) {
        return escolaService.atualizar(id, request);
    }

    @PatchMapping("/{id}/ativar")
    public EscolaResponse ativar(@PathVariable UUID id) {
        return escolaService.ativar(id);
    }

    @PatchMapping("/{id}/suspender")
    public EscolaResponse suspender(@PathVariable UUID id) {
        return escolaService.suspender(id);
    }
}
