package br.pucminas.eduka.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.dto.EscolaUpdateRequest;
import br.pucminas.eduka.backend.service.EscolaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/escolas")
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
