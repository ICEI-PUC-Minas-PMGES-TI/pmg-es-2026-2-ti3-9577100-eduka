package br.pucminas.eduka.backend.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.dto.CursoResponse;
import br.pucminas.eduka.backend.dto.DisciplinaResponse;
import br.pucminas.eduka.backend.dto.PeriodoLetivoResponse;
import br.pucminas.eduka.backend.dto.TurmaResponse;
import br.pucminas.eduka.backend.repository.CursoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;

@Service
@Transactional(readOnly = true)
public class AcademicoSecretariaService {

    private final PeriodoLetivoRepository periodoLetivoRepository;
    private final CursoRepository cursoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final TurmaRepository turmaRepository;

    public AcademicoSecretariaService(PeriodoLetivoRepository periodoLetivoRepository,
                                      CursoRepository cursoRepository,
                                      DisciplinaRepository disciplinaRepository,
                                      TurmaRepository turmaRepository) {
        this.periodoLetivoRepository = periodoLetivoRepository;
        this.cursoRepository = cursoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.turmaRepository = turmaRepository;
    }

    public List<PeriodoLetivoResponse> listarPeriodosLetivos(UUID escolaId) {
        return periodoLetivoRepository.findAllByEscolaId(escolaId).stream()
                .map(p -> toResponse(p, escolaId))
                .toList();
    }

    public List<CursoResponse> listarCursos(UUID escolaId) {
        return cursoRepository.findAllByEscolaId(escolaId).stream()
                .map(c -> toResponse(c, escolaId))
                .toList();
    }

    public List<DisciplinaResponse> listarDisciplinas(UUID escolaId) {
        return disciplinaRepository.findAllByEscolaId(escolaId).stream()
                .map(d -> toResponse(d, escolaId))
                .toList();
    }

    public List<TurmaResponse> listarTurmas(UUID escolaId) {
        return turmaRepository.findAllByEscolaId(escolaId).stream()
                .map(t -> toResponse(t, escolaId))
                .toList();
    }

    private PeriodoLetivoResponse toResponse(PeriodoLetivo p, UUID escolaId) {
        return PeriodoLetivoResponse.builder()
                .id(p.getId())
                .escolaId(escolaId)
                .nome(p.getNome())
                .dataInicio(p.getDataInicio())
                .dataFim(p.getDataFim())
                .ativo(p.getAtivo())
                .build();
    }

    private CursoResponse toResponse(Curso c, UUID escolaId) {
        return CursoResponse.builder()
                .id(c.getId())
                .escolaId(escolaId)
                .nome(c.getNome())
                .codigo(c.getCodigo())
                .descricao(c.getDescricao())
                .ativo(c.getAtivo())
                .build();
    }

    private DisciplinaResponse toResponse(Disciplina d, UUID escolaId) {
        Curso curso = d.getCurso();
        return DisciplinaResponse.builder()
                .id(d.getId())
                .escolaId(escolaId)
                .cursoId(curso != null ? curso.getId() : null)
                .cursoNome(curso != null ? curso.getNome() : null)
                .nome(d.getNome())
                .codigo(d.getCodigo())
                .cargaHoraria(d.getCargaHoraria())
                .descricao(d.getDescricao())
                .ativa(d.getAtiva())
                .build();
    }

    private TurmaResponse toResponse(Turma t, UUID escolaId) {
        return TurmaResponse.builder()
                .id(t.getId())
                .escolaId(escolaId)
                .periodoLetivoId(t.getPeriodoLetivo().getId())
                .periodoLetivoNome(t.getPeriodoLetivo().getNome())
                .cursoId(t.getCurso().getId())
                .cursoNome(t.getCurso().getNome())
                .nome(t.getNome())
                .codigo(t.getCodigo())
                .vagas(t.getVagas())
                .turno(t.getTurno())
                .ativa(t.getAtiva())
                .disciplinas(t.getDisciplinas().stream()
                        .map(d -> toResponse(d, escolaId))
                        .collect(Collectors.toSet()))
                .build();
    }
}
