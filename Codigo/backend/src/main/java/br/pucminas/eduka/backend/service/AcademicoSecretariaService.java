package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.StatusMatricula;
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
import br.pucminas.eduka.backend.exception.RegistroEmUsoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.CursoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;

/**
 * Estrutura academica da escola: periodos letivos, cursos, disciplinas e
 * turmas. A escola sempre vem do token (o controller repassa a do usuario
 * autenticado); curso, periodo e disciplinas referenciados num cadastro so
 * sao aceitos se forem da mesma escola.
 *
 * <p>
 * Edicao substitui o registro inteiro. Exclusao so passa se nada depende do
 * registro; senao responde 409 ({@link RegistroEmUsoException}) dizendo o
 * que impede. Assim nenhum historico (matricula, avaliacao, alocacao) some
 * por tabela.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class AcademicoSecretariaService {

    private final PeriodoLetivoRepository periodoLetivoRepository;
    private final CursoRepository cursoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final TurmaRepository turmaRepository;
    private final EscolaRepository escolaRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final Clock clock;

    public AcademicoSecretariaService(PeriodoLetivoRepository periodoLetivoRepository,
                                      CursoRepository cursoRepository,
                                      DisciplinaRepository disciplinaRepository,
                                      TurmaRepository turmaRepository,
                                      EscolaRepository escolaRepository,
                                      MatriculaTurmaRepository matriculaRepository,
                                      AlocacaoProfessorRepository alocacaoRepository,
                                      AvaliacaoRepository avaliacaoRepository,
                                      Clock clock) {
        this.periodoLetivoRepository = periodoLetivoRepository;
        this.cursoRepository = cursoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.turmaRepository = turmaRepository;
        this.escolaRepository = escolaRepository;
        this.matriculaRepository = matriculaRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.clock = clock;
    }

    @Transactional
    public PeriodoLetivoResponse cadastrarPeriodoLetivo(UUID escolaId, PeriodoLetivoRequest request) {
        exigirDatasEmOrdem(request);
        PeriodoLetivo periodo = PeriodoLetivo.builder()
                .escola(escola(escolaId))
                .nome(request.getNome().trim())
                .dataInicio(request.getDataInicio())
                .dataFim(request.getDataFim())
                // Periodo que ainda nao comecou (planejado) so recebe matricula depois de Abrir matriculas.
                .matriculasAbertas(!request.getDataInicio().isAfter(hoje()))
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
        PeriodoLetivo periodo = periodoDaEscola(request.getPeriodoLetivoId(), escolaId);
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
        return toResponse(turmaRepository.save(turma), escolaId, 0);
    }

    // ----- Encerrar e abrir matriculas (IU09) -----

    /**
     * Encerra o periodo em andamento antes do fim: ele deixa de receber
     * matricula nova. As matriculas e notas ja feitas ficam como estao.
     *
     * @throws IllegalArgumentException se o periodo nao esta em andamento
     */
    @Transactional
    public PeriodoLetivoResponse encerrarPeriodoLetivo(UUID escolaId, Long id) {
        LocalDate hoje = hoje();
        PeriodoLetivo periodo = periodoDaEscola(id, escolaId);
        boolean emAndamento = periodo.getEncerradoEm() == null && !hoje.isBefore(periodo.getDataInicio())
                && !hoje.isAfter(periodo.getDataFim());
        if (!emAndamento) {
            throw new IllegalArgumentException("So o periodo em andamento pode ser encerrado");
        }
        periodo.setEncerradoEm(hoje);
        return toResponse(periodoLetivoRepository.save(periodo), escolaId);
    }

    /**
     * Libera as matriculas de um periodo planejado (que ainda nao comecou).
     *
     * @throws IllegalArgumentException se o periodo ja comecou ou foi encerrado
     */
    @Transactional
    public PeriodoLetivoResponse abrirMatriculas(UUID escolaId, Long id) {
        LocalDate hoje = hoje();
        PeriodoLetivo periodo = periodoDaEscola(id, escolaId);
        if (periodo.getEncerradoEm() != null || !hoje.isBefore(periodo.getDataInicio())) {
            throw new IllegalArgumentException("So o periodo planejado tem as matriculas abertas pela secretaria");
        }
        periodo.setMatriculasAbertas(true);
        return toResponse(periodoLetivoRepository.save(periodo), escolaId);
    }

    // ----- Edicao -----

    @Transactional
    public PeriodoLetivoResponse atualizarPeriodoLetivo(UUID escolaId, Long id, PeriodoLetivoRequest request) {
        exigirDatasEmOrdem(request);
        PeriodoLetivo periodo = periodoDaEscola(id, escolaId);
        periodo.setNome(request.getNome().trim());
        periodo.setDataInicio(request.getDataInicio());
        periodo.setDataFim(request.getDataFim());
        return toResponse(periodoLetivoRepository.save(periodo), escolaId);
    }

    @Transactional
    public CursoResponse atualizarCurso(UUID escolaId, Long id, CursoRequest request) {
        Curso curso = cursoDaEscola(id, escolaId);
        curso.setNome(request.getNome().trim());
        curso.setCodigo(opcional(request.getCodigo()));
        curso.setDescricao(opcional(request.getDescricao()));
        return toResponse(cursoRepository.save(curso), escolaId);
    }

    @Transactional
    public DisciplinaResponse atualizarDisciplina(UUID escolaId, Long id, DisciplinaRequest request) {
        Disciplina disciplina = disciplinaDaEscola(id, escolaId);
        disciplina.setCurso(request.getCursoId() == null ? null : cursoDaEscola(request.getCursoId(), escolaId));
        disciplina.setNome(request.getNome().trim());
        disciplina.setCodigo(opcional(request.getCodigo()));
        disciplina.setCargaHoraria(request.getCargaHoraria());
        disciplina.setDescricao(opcional(request.getDescricao()));
        return toResponse(disciplinaRepository.save(disciplina), escolaId);
    }

    /**
     * Substitui os dados e a grade da turma. Tirar da grade uma disciplina
     * leva junto a alocacao do professor nela (FK em cascata, V15).
     *
     * @throws IllegalArgumentException se as vagas ficam abaixo das
     *                                  matriculas ativas
     * @throws RegistroEmUsoException   se sai da grade uma disciplina que ja
     *                                  tem avaliacao nesta turma
     */
    @Transactional
    public TurmaResponse atualizarTurma(UUID escolaId, Long id, TurmaRequest request) {
        Turma turma = turmaDaEscola(id, escolaId);
        long ativas = matriculaRepository.countByEscolaIdAndTurmaIdAndStatus(escolaId, id, StatusMatricula.ATIVA);
        if (request.getVagas() < ativas) {
            throw new IllegalArgumentException(
                    "A turma tem %d matriculas ativas; as vagas nao podem ficar abaixo disso".formatted(ativas));
        }
        Set<Disciplina> grade = disciplinasDaEscola(request.getDisciplinaIds(), escolaId);
        Set<Long> idsDaGrade = grade.stream().map(Disciplina::getId).collect(Collectors.toSet());
        for (Disciplina saindo : turma.getDisciplinas()) {
            if (!idsDaGrade.contains(saindo.getId())
                    && avaliacaoRepository.existsByEscolaIdAndTurmaIdAndDisciplinaId(escolaId, id, saindo.getId())) {
                throw new RegistroEmUsoException("A disciplina %s tem avaliacoes nesta turma e nao pode sair da grade"
                        .formatted(saindo.getNome()));
            }
        }

        turma.setPeriodoLetivo(periodoDaEscola(request.getPeriodoLetivoId(), escolaId));
        turma.setCurso(cursoDaEscola(request.getCursoId(), escolaId));
        turma.setNome(request.getNome().trim());
        turma.setCodigo(opcional(request.getCodigo()));
        turma.setVagas(request.getVagas());
        turma.setTurno(request.getTurno());
        turma.getDisciplinas().clear();
        turma.getDisciplinas().addAll(grade);
        return toResponse(turmaRepository.save(turma), escolaId, ativas);
    }

    // ----- Exclusao -----

    @Transactional
    public void excluirPeriodoLetivo(UUID escolaId, Long id) {
        PeriodoLetivo periodo = periodoDaEscola(id, escolaId);
        if (turmaRepository.existsByEscolaIdAndPeriodoLetivoId(escolaId, id)) {
            throw new RegistroEmUsoException("O periodo letivo tem turmas; exclua ou mude o periodo delas antes");
        }
        periodoLetivoRepository.delete(periodo);
    }

    @Transactional
    public void excluirCurso(UUID escolaId, Long id) {
        Curso curso = cursoDaEscola(id, escolaId);
        if (turmaRepository.existsByEscolaIdAndCursoId(escolaId, id)) {
            throw new RegistroEmUsoException("O curso tem turmas; exclua ou mude o curso delas antes");
        }
        if (disciplinaRepository.existsByEscolaIdAndCursoId(escolaId, id)) {
            throw new RegistroEmUsoException("O curso tem disciplinas vinculadas; exclua ou desvincule-as antes");
        }
        cursoRepository.delete(curso);
    }

    @Transactional
    public void excluirDisciplina(UUID escolaId, Long id) {
        Disciplina disciplina = disciplinaDaEscola(id, escolaId);
        if (turmaRepository.existsByEscolaIdAndDisciplinas_Id(escolaId, id)) {
            throw new RegistroEmUsoException("A disciplina esta na grade de alguma turma; tire-a da grade antes");
        }
        disciplinaRepository.delete(disciplina);
    }

    @Transactional
    public void excluirTurma(UUID escolaId, Long id) {
        Turma turma = turmaDaEscola(id, escolaId);
        if (matriculaRepository.existsByEscolaIdAndTurmaId(escolaId, id)) {
            throw new RegistroEmUsoException("A turma tem alunos matriculados e nao pode ser excluida");
        }
        if (avaliacaoRepository.existsByEscolaIdAndTurmaId(escolaId, id)) {
            throw new RegistroEmUsoException("A turma tem avaliacoes cadastradas e nao pode ser excluida");
        }
        if (alocacaoRepository.existsByEscolaIdAndTurmaId(escolaId, id)) {
            throw new RegistroEmUsoException("A turma tem professores alocados; desaloque-os antes");
        }
        turmaRepository.delete(turma);
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

    /** Data de hoje no fuso da escola, como na matricula. */
    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(MatriculaTurmaService.FUSO_ESCOLA));
    }

    private PeriodoLetivo periodoDaEscola(Long id, UUID escolaId) {
        return periodoLetivoRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Periodo letivo nao encontrado"));
    }

    private Disciplina disciplinaDaEscola(Long id, UUID escolaId) {
        return disciplinaRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Disciplina nao encontrada"));
    }

    private Turma turmaDaEscola(Long id, UUID escolaId) {
        return turmaRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma nao encontrada"));
    }

    private static void exigirDatasEmOrdem(PeriodoLetivoRequest request) {
        if (request.getDataFim().isBefore(request.getDataInicio())) {
            throw new IllegalArgumentException("A data de fim deve ser igual ou posterior a data de inicio");
        }
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
        // Uma contagem agrupada para todas as turmas, em vez de uma por turma.
        Map<Long, Long> ativasPorTurma = matriculaRepository.contarAtivasPorTurma(escolaId).stream()
                .collect(Collectors.toMap(MatriculaTurmaRepository.AtivasNaTurma::getTurmaId,
                        MatriculaTurmaRepository.AtivasNaTurma::getQuantidade));
        return turmaRepository.findAllByEscolaId(escolaId).stream()
                .map(t -> toResponse(t, escolaId, ativasPorTurma.getOrDefault(t.getId(), 0L)))
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
                .matriculasAbertas(p.isMatriculasAbertas())
                .encerradoEm(p.getEncerradoEm())
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

    private TurmaResponse toResponse(Turma t, UUID escolaId, long matriculasAtivas) {
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
                .matriculasAtivas(matriculasAtivas)
                .build();
    }
}
