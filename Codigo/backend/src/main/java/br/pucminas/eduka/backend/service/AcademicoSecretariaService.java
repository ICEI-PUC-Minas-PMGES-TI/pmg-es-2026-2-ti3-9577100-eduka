package br.pucminas.eduka.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.dto.CursoResponse;
import br.pucminas.eduka.backend.dto.DisciplinaResponse;
import br.pucminas.eduka.backend.dto.PeriodoLetivoResponse;
import br.pucminas.eduka.backend.dto.TurmaResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.EscolaRepository;

@Service
public class AcademicoSecretariaService {

    private final EscolaRepository escolaRepository;

    public AcademicoSecretariaService(EscolaRepository escolaRepository) {
        this.escolaRepository = escolaRepository;
    }

    public Escola buscarEscola(UUID escolaId) {
        // escolaId passou a ser UUID (compatível com CrudRepository<Escola, UUID>)
        return escolaRepository.findById(escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Escola não encontrada"));
    }

    public PeriodoLetivoResponse criarPeriodoLetivoResponse(UUID escolaId) {
        return PeriodoLetivoResponse.builder()
                .escolaId(escolaId) // Passa UUID em vez de Long
                .build();
    }

    public CursoResponse criarCursoResponse(UUID escolaId) {
        return CursoResponse.builder()
                .escolaId(escolaId) // Passa UUID em vez de Long
                .build();
    }

    public DisciplinaResponse criarDisciplinaResponse(UUID escolaId) {
        return DisciplinaResponse.builder()
                .escolaId(escolaId) // Passa UUID em vez de Long
                .build();
    }

    public TurmaResponse criarTurmaResponse(UUID escolaId) {
        return TurmaResponse.builder()
                .escolaId(escolaId) // Passa UUID em vez de Long
                .build();
    }
}
