package br.pucminas.eduka.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.dto.CursoRequest;
import br.pucminas.eduka.backend.dto.CursoResponse;
import br.pucminas.eduka.backend.dto.DisciplinaRequest;
import br.pucminas.eduka.backend.dto.DisciplinaResponse;
import br.pucminas.eduka.backend.dto.PeriodoLetivoRequest;
import br.pucminas.eduka.backend.dto.PeriodoLetivoResponse;
import br.pucminas.eduka.backend.dto.TurmaRequest;
import br.pucminas.eduka.backend.dto.TurmaResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.CursoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;

/**
 * Estrutura academica da escola: periodos letivos, cursos, disciplinas e
 * turmas. A escola sempre vem do token (o controller repassa a do usuario
 * autenticado); curso, periodo e disciplinas referenciados num cadastro so
 * sao aceitos se forem da mesma escola.
 */
@Service
@Transactional(readOnly = true)
public class AcademicoSecretariaService {

    private final PeriodoLetivoRepository periodoLetivoRepository;
    private final CursoRepository cursoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final TurmaRepository turmaRepository;
    private final EscolaRepository escolaRepository;

    public AcademicoSecretariaService(PeriodoLetivoRepository periodoLetivoRepository,
                                      CursoRepository cursoRepository,
                                      DisciplinaRepository disciplinaRepository,
                                      TurmaRepository turmaRepository,
                                      EscolaRepository escolaRepository) {
        this.periodoLetivoRepository = periodoLetivoRepository;
        this.cursoRepository = cursoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.turmaRepository = turmaRepository;
        this.escolaRepository = escolaRepository;
    }

    @Transactional
    public PeriodoLetivoResponse cadastrarPeriodoLetivo(UUID escolaId, PeriodoLetivoRequest request) {
        if (request.getDataFim().isBefore(request.getDataInicio())) {
            throw new IllegalArgumentException("A data de fim deve ser igual ou posterior a data de inicio");
        }
        PeriodoLetivo periodo = PeriodoLetivo.builder()
                .escola(escola(escolaId))
                .nome(request.getNome().trim())
                .dataInicio(request.getDataInicio())
                .dataFim(request.getDataFim())
                .build();
        return toResponse(periodoLetivoRepository.save(periodo), escolaId);
    }

    @Transactional
    public CursoResponse cadastrarCurso(UUID escolaId, CursoRequest request) {
        Curso curso = Curso.builder()
                .escola(escola(escolaId))
                .nome(request.getNome().trim())
                .codigo(opcional(request.getCodigo()))
                .descricao(opcional(request.getDescricao()))
                .build();
        return toResponse(cursoRepository.save(curso), escolaId);
    }

    @Transactional
    public DisciplinaResponse cadastrarDisciplina(UUID escolaId, DisciplinaRequest request) {
        Curso curso = request.getCursoId() == null ? null : cursoDaEscola(request.getCursoId(), escolaId);
        Disciplina disciplina = Disciplina.builder()
                .escola(escola(escolaId))
                .curso(curso)
                .nome(request.getNome().trim())
                .codigo(opcional(request.getCodigo()))
                .cargaHoraria(request.getCargaHoraria())
                .descricao(opcional(request.getDescricao()))
                .build();
        return toResponse(disciplinaRepository.save(disciplina), escolaId);
    }

    @Transactional
    public TurmaResponse cadastrarTurma(UUID escolaId, TurmaRequest request) {
        PeriodoLetivo periodo = periodoLetivoRepository.findByIdAndEscolaId(request.getPeriodoLetivoId(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Periodo letivo nao encontrado"));
        Curso curso = cursoDaEscola(request.getCursoId(), escolaId);
        Turma turma = Turma.builder()
                .escola(escola(escolaId))
                .periodoLetivo(periodo)
                .curso(curso)
                .nome(request.getNome().trim())
                .codigo(opcional(request.getCodigo()))
                .vagas(request.getVagas())
                .turno(request.getTurno())
                .disciplinas(disciplinasDaEscola(request.getDisciplinaIds(), escolaId))
                .build();
        return toResponse(turmaRepository.save(turma), escolaId);
    }

    /** Referencia sem SELECT: a escola do token sempre existe. */
    private Escola escola(UUID escolaId) {
        return escolaRepository.getReferenceById(escolaId);
    }

    /** Curso de outra escola responde como inexistente, para nao confirmar o id. */
    private Curso cursoDaEscola(Long cursoId, UUID escolaId) {
        return cursoRepository.findByIdAndEscolaId(cursoId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Curso nao encontrado"));
    }

    private Set<Disciplina> disciplinasDaEscola(Set<Long> ids, UUID escolaId) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<Disciplina> encontradas = disciplinaRepository.findAllByIdInAndEscolaId(List.copyOf(ids), escolaId);
        if (encontradas.size() != ids.size()) {
            throw new RecursoNaoEncontradoException("Disciplina nao encontrada");
        }
        return new HashSet<>(encontradas);
    }

    /** Campo opcional vazio vira null, para nao gravar string vazia. */
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
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
