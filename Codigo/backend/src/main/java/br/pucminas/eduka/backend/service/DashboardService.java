package br.pucminas.eduka.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.Avaliacao;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.DashboardEscolaResponse;
import br.pucminas.eduka.backend.dto.DashboardProfessorResponse;
import br.pucminas.eduka.backend.dto.DashboardProfessorResponse.AvaliacaoResumo;
import br.pucminas.eduka.backend.dto.DashboardSecretariaResponse;
import br.pucminas.eduka.backend.dto.DashboardSecretariaResponse.OcupacaoTurma;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.CursoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.NotaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Metricas dos dashboards por perfil (RF20). Tudo e contado na escola do
 * token; o do professor, so nas turmas/disciplinas alocadas a ele.
 *
 * <p>
 * Alunos, professores, cursos, disciplinas, matriculas, avaliacoes e notas
 * sao contados e somados no banco: matriculas e notas acumulam de periodo em
 * periodo, e carregar o historico so para contar pesaria cada vez mais.
 * Turmas e periodos, que a conta precisa ver um a um, continuam em memoria.
 * </p>
 *
 * <p>
 * "Hoje" e o dia no fuso da escola, como na data de matricula (ver
 * {@link MatriculaTurmaService}).
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");
    private static final int DIAS_RECENTES = 30;

    private final AlunoRepository alunoRepository;
    private final ProfessorRepository professorRepository;
    private final UsuarioRepository usuarioRepository;
    private final CursoRepository cursoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final PeriodoLetivoRepository periodoLetivoRepository;
    private final TurmaRepository turmaRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final NotaRepository notaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public DashboardService(AlunoRepository alunoRepository, ProfessorRepository professorRepository,
            UsuarioRepository usuarioRepository, CursoRepository cursoRepository,
            DisciplinaRepository disciplinaRepository, PeriodoLetivoRepository periodoLetivoRepository,
            TurmaRepository turmaRepository, MatriculaTurmaRepository matriculaRepository,
            AlocacaoProfessorRepository alocacaoRepository, AvaliacaoRepository avaliacaoRepository,
            NotaRepository notaRepository, IsolamentoEscola isolamentoEscola, Clock clock) {
        this.alunoRepository = alunoRepository;
        this.professorRepository = professorRepository;
        this.usuarioRepository = usuarioRepository;
        this.cursoRepository = cursoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.periodoLetivoRepository = periodoLetivoRepository;
        this.turmaRepository = turmaRepository;
        this.matriculaRepository = matriculaRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.notaRepository = notaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ MASTER

    public DashboardEscolaResponse escola() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        LocalDate hoje = hoje();

        long professores = professorRepository.countByEscolaId(escolaId);
        long professoresAtivos = professorRepository.countByEscolaIdAndAtivoTrue(escolaId);
        long alunos = alunoRepository.countByEscolaIdAndAnonimizadoEmIsNull(escolaId);
        long alunosAtivos = matriculaRepository.contarAlunosComMatriculaAtiva(escolaId);
        var pessoas = new DashboardEscolaResponse.Pessoas(
                alunos,
                alunosAtivos,
                alunosSemTurma(alunos, alunosAtivos),
                professoresAtivos,
                professores - professoresAtivos,
                usuariosAtivos(escolaId, PerfilUsuario.SECRETARIA),
                usuariosAtivos(escolaId, PerfilUsuario.FINANCEIRO));

        List<Turma> turmas = turmaRepository.findAllByEscolaId(escolaId);
        List<Turma> ativas = turmasAtivas(turmas);
        String periodoAtual = periodoLetivoRepository.findAllByEscolaId(escolaId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .filter(p -> !hoje.isBefore(p.getDataInicio()) && !hoje.isAfter(p.getDataFim()))
                .max(Comparator.comparing(PeriodoLetivo::getDataInicio))
                .map(PeriodoLetivo::getNome)
                .orElse(null);
        var estrutura = new DashboardEscolaResponse.Estrutura(
                cursoRepository.countByEscolaId(escolaId),
                disciplinaRepository.countByEscolaId(escolaId),
                ativas.size(),
                turmas.size(),
                periodoAtual);

        long vagas = ativas.stream().mapToLong(Turma::getVagas).sum();
        Map<Long, Long> ativasPorTurma = ativasPorTurma(escolaId);
        long ocupadas = ativas.stream().mapToLong(t -> ativasPorTurma.getOrDefault(t.getId(), 0L)).sum();
        var ocupacao = new DashboardEscolaResponse.Ocupacao(vagas, ocupadas, percentual(ocupadas, vagas));

        return new DashboardEscolaResponse(pessoas, estrutura, ocupacao, desempenho(escolaId));
    }

    // -------------------------------------------------------------- SECRETARIA

    public DashboardSecretariaResponse secretaria() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        LocalDate desde = hoje().minusDays(DIAS_RECENTES);

        Map<StatusMatricula, Long> porSituacao = new EnumMap<>(StatusMatricula.class);
        for (StatusMatricula status : StatusMatricula.values()) {
            porSituacao.put(status, 0L);
        }
        matriculaRepository.contarPorSituacao(escolaId)
                .forEach(s -> porSituacao.put(s.getStatus(), s.getQuantidade()));
        long recentes = matriculaRepository.countByEscolaIdAndDataMatriculaGreaterThanEqual(escolaId, desde);
        long alunosSemTurma = alunosSemTurma(alunoRepository.countByEscolaIdAndAnonimizadoEmIsNull(escolaId),
                matriculaRepository.contarAlunosComMatriculaAtiva(escolaId));

        Map<Long, Long> ativasPorTurma = ativasPorTurma(escolaId);
        List<OcupacaoTurma> turmas = turmasAtivas(turmaRepository.findAllByEscolaId(escolaId)).stream()
                .map(t -> new OcupacaoTurma(t.getId(), t.getNome(), t.getPeriodoLetivo().getNome(),
                        t.getCurso().getNome(), t.getTurno(), t.getVagas(), ativasPorTurma.getOrDefault(t.getId(), 0L)))
                .sorted(Comparator.comparingDouble(DashboardService::fracaoOcupada).reversed()
                        .thenComparing(OcupacaoTurma::turmaNome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        long ofertadas = turmas.stream().mapToLong(OcupacaoTurma::vagas).sum();
        long ocupadas = turmas.stream().mapToLong(OcupacaoTurma::matriculasAtivas).sum();
        long livres = turmas.stream().mapToLong(t -> Math.max(0, t.vagas() - t.matriculasAtivas())).sum();
        long lotadas = turmas.stream().filter(t -> t.matriculasAtivas() >= t.vagas()).count();

        return new DashboardSecretariaResponse(porSituacao, recentes, alunosSemTurma,
                new DashboardSecretariaResponse.Vagas(ofertadas, ocupadas, livres, lotadas), turmas);
    }

    // --------------------------------------------------------------- PROFESSOR

    /** @throws RecursoNaoEncontradoException se o login nao e de um professor da escola */
    public DashboardProfessorResponse professor() {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        UUID escolaId = usuario.escolaId();
        Professor professor = professorRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor nao encontrado"));
        LocalDate hoje = hoje();

        List<AlocacaoProfessor> alocacoes = alocacaoRepository
                .findAllComTurmaEDisciplinaByEscolaIdAndProfessorId(escolaId, professor.getId());
        if (alocacoes.isEmpty()) {
            return new DashboardProfessorResponse(0, 0, 0, 0, List.of(), List.of());
        }
        Set<Long> turmaIds = alocacoes.stream().map(a -> a.getTurma().getId()).collect(Collectors.toSet());
        Set<String> pares = alocacoes.stream()
                .map(a -> par(a.getTurma().getId(), a.getDisciplina().getId()))
                .collect(Collectors.toSet());

        // Alunos ATIVOS de cada turma do professor.
        Map<Long, Set<UUID>> ativosPorTurma = new HashMap<>();
        for (Long turmaId : turmaIds) {
            ativosPorTurma.put(turmaId, matriculaRepository.findAllByEscolaIdAndTurmaId(escolaId, turmaId).stream()
                    .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                    .map(m -> m.getAluno().getId())
                    .collect(Collectors.toSet()));
        }
        long alunos = ativosPorTurma.values().stream().flatMap(Set::stream).distinct().count();

        // So as avaliacoes das disciplinas dele (a turma pode ter outras disciplinas).
        List<Avaliacao> avaliacoes = avaliacaoRepository.findAllByEscolaIdAndTurmaIdIn(escolaId, turmaIds).stream()
                .filter(a -> pares.contains(par(a.getTurma().getId(), a.getDisciplina().getId())))
                .toList();
        Map<UUID, Set<UUID>> comNota = new HashMap<>();
        if (!avaliacoes.isEmpty()) {
            notaRepository.listarLancadas(escolaId, avaliacoes.stream().map(Avaliacao::getId).toList())
                    .forEach(n -> comNota.computeIfAbsent(n.getAvaliacaoId(), k -> new HashSet<>()).add(n.getAlunoId()));
        }
        Function<Avaliacao, AvaliacaoResumo> resumo = a -> {
            Set<UUID> ativos = ativosPorTurma.getOrDefault(a.getTurma().getId(), Set.of());
            Set<UUID> lancados = comNota.getOrDefault(a.getId(), Set.of());
            long faltando = ativos.stream().filter(id -> !lancados.contains(id)).count();
            return new AvaliacaoResumo(a.getId(), a.getTipo(), a.getTitulo(), a.getData(), a.getValor(),
                    a.getTurma().getId(), a.getTurma().getNome(), a.getDisciplina().getId(),
                    a.getDisciplina().getNome(), faltando);
        };

        LocalDate limite = hoje.plusDays(DIAS_RECENTES);
        Comparator<AvaliacaoResumo> porData = Comparator.comparing(AvaliacaoResumo::data)
                .thenComparing(AvaliacaoResumo::titulo, String.CASE_INSENSITIVE_ORDER);
        List<AvaliacaoResumo> proximas = avaliacoes.stream()
                .filter(a -> !a.getData().isBefore(hoje) && !a.getData().isAfter(limite))
                .map(resumo)
                .sorted(porData)
                .toList();
        List<AvaliacaoResumo> pendentes = avaliacoes.stream()
                .filter(a -> a.getData().isBefore(hoje))
                .map(resumo)
                .filter(r -> r.notasFaltando() > 0)
                .sorted(porData)
                .toList();

        long disciplinas = alocacoes.stream().map(a -> a.getDisciplina().getId()).distinct().count();
        return new DashboardProfessorResponse(turmaIds.size(), disciplinas, alunos, avaliacoes.size(), proximas,
                pendentes);
    }

    // ------------------------------------------------------------------ comum

    private DashboardEscolaResponse.Desempenho desempenho(UUID escolaId) {
        NotaRepository.Aproveitamento notas = notaRepository.somarAproveitamento(escolaId);
        BigDecimal obtido = Objects.requireNonNullElse(notas.getObtido(), BigDecimal.ZERO);
        BigDecimal possivel = Objects.requireNonNullElse(notas.getPossivel(), BigDecimal.ZERO);
        Integer aproveitamento = possivel.signum() == 0 ? null
                : obtido.multiply(BigDecimal.valueOf(100)).divide(possivel, 0, RoundingMode.HALF_UP).intValue();
        return new DashboardEscolaResponse.Desempenho(avaliacaoRepository.countByEscolaId(escolaId),
                notas.getTotal(), aproveitamento);
    }

    private long usuariosAtivos(UUID escolaId, PerfilUsuario perfil) {
        return usuarioRepository.findAllByEscolaIdAndPerfil(escolaId, perfil).stream().filter(Usuario::isAtivo).count();
    }

    /** Alunos cadastrados sem nenhuma matricula ATIVA; a mesma conta no MASTER e na SECRETARIA. */
    private static long alunosSemTurma(long alunos, long comMatriculaAtiva) {
        return alunos - comMatriculaAtiva;
    }

    private Map<Long, Long> ativasPorTurma(UUID escolaId) {
        return matriculaRepository.contarAtivasPorTurma(escolaId).stream()
                .collect(Collectors.toMap(MatriculaTurmaRepository.AtivasNaTurma::getTurmaId,
                        MatriculaTurmaRepository.AtivasNaTurma::getQuantidade));
    }

    private static List<Turma> turmasAtivas(List<Turma> turmas) {
        return turmas.stream().filter(t -> Boolean.TRUE.equals(t.getAtiva())).toList();
    }

    private static double fracaoOcupada(OcupacaoTurma turma) {
        return turma.vagas() == 0 ? 1 : (double) turma.matriculasAtivas() / turma.vagas();
    }

    /** 0 a 100, arredondado; nulo quando nao ha base. */
    private static Integer percentual(long parte, long total) {
        return total == 0 ? null : (int) Math.round(parte * 100.0 / total);
    }

    private static String par(Long turmaId, Long disciplinaId) {
        return turmaId + ":" + disciplinaId;
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }
}
