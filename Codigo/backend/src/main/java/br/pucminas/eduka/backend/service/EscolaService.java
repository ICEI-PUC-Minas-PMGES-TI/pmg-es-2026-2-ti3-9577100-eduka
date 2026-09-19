package br.pucminas.eduka.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.repository.EscolaRepository;

@Service
public class EscolaService {

    private final EscolaRepository escolaRepository;

    public EscolaService(EscolaRepository escolaRepository) {
        this.escolaRepository = escolaRepository;
    }

    public List<EscolaResponse> listar() {
        return escolaRepository.findAll().stream()
                .map(EscolaResponse::from)
                .toList();
    }

    public EscolaResponse buscarPorId(UUID id) {
        return escolaRepository.findById(id)
                .map(EscolaResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "escola não encontrada"));
    }
}
