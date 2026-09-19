package br.pucminas.eduka.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.service.EscolaService;

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
}
