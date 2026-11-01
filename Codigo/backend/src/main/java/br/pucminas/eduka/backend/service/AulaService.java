package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Aula;
import br.pucminas.eduka.backend.domain.Frequencia;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.AtualizarAulaRequest;
import br.pucminas.eduka.backend.dto.AulaRequest;
import br.pucminas.eduka.backend.dto.AulaResponse;
import br.pucminas.eduka.backend.dto.AulaResumoResponse;
import br.pucminas.eduka.backend.dto.ChamadaItem;
import br.pucminas.eduka.backend.dto.FrequenciaAlunoResponse;
import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AulaRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Aulas e chamada, pelo professor autenticado (portal do professor).
 *
 * <p>
 * O professor e a escola saem do token. Ele so enxerga e altera aulas das
 * disciplinas em que esta alocado hoje (ver {@link AlocacaoProfessorService});
 * fora disso, a turma/disciplina ou a aula respondem 404, como se nao
 * existissem. Assim, quando a secretaria troca o professor de uma disciplina,
 * as aulas passam para o novo professor.
 * </p>
 *
 * <p>
 * Regras da chamada:
 * </p>
 * <ul>
 * <li>A data da aula nao pode ser futura (dia de hoje no fuso da escola).</li>
 * <li>A chamada e completa: todo aluno com matricula ATIVA na turma entra na
 * lista, uma vez cada. Faltar aluno na lista e 400, para a frequencia nao
 * ficar com buracos que distorcem o percentual de faltas.</li>
 * <li>Aluno sem matricula ativa na turma e 400. Ao corrigir uma aula, quem ja
 * tinha presenca registrada nela e saiu da turma depois ainda pode ser
 * corrigido, mas deixa de ser exigido.</li>
 * <li>Corrigir substitui o conteudo e a presenca de cada aluno enviado; a data
 * nao muda.</li>
 * </ul>
 */
@Service
public class AulaService {

    /** A escola conta o dia no horario de Brasilia, nao em UTC. */
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final AulaRepository aulaRepository;
    private final FrequenciaRepository frequenciaRepository;
    private final ProfessorRepository professorRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final TurmaRepository turmaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public AulaService(AulaRepository aulaRepository, FrequenciaRepository frequenciaRepository,
            ProfessorRepository professorRepository, AlocacaoProfessorRepository alocacaoRepository,
            TurmaRepository turmaRepository, DisciplinaRepository disciplinaRepository,
            MatriculaTurmaRepository matriculaRepository, PessoaRepository pessoaRepository,
            IsolamentoEscola isolamentoEscola, Clock clock) {
        this.aulaRepository = aulaRepository;
        this.frequenciaRepository = frequenciaRepository;
        this.professorRepository = professorRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.turmaRepository = turmaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.matriculaRepository = matriculaRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    /**
     * Pagina das aulas da disciplina na turma, por data (a mais recente
     * primeiro, salvo {@code direcao}), cada uma com os totais da chamada.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<AulaResumoResponse> listar(Long turmaId, Long disciplinaId, Paginacao paginacao,
            Direction direcao) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirAlocacao(professorAtual(escolaId), escolaId, turmaId, disciplinaId);

        var pagina = aulaRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaId(escolaId, turmaId, disciplinaId,
                paginacao.comOrdem(Ordenacao.noBanco(Ordenacao.ou(direcao, Direction.DESC), "data")));
        if (pagina.isEmpty()) {
            return PaginaResponse.de(pagina, aula -> AulaResumoResponse.de(aula, 0, 0));
        }
        Map<UUID, FrequenciaRepository.TotaisDaAula> totais = frequenciaRepository
                .contarPorAula(escolaId, pagina.getContent().stream().map(Aula::getId).toList()).stream()
                .collect(Collectors.toMap(FrequenciaRepository.TotaisDaAula::getAulaId, Function.identity()));
        return PaginaResponse.de(pagina, aula -> {
            FrequenciaRepository.TotaisDaAula total = totais.get(aula.getId());
            return AulaResumoResponse.de(aula, total != null ? total.getPresentes() : 0,
                    total != null ? total.getFaltas() : 0);
        });
    }

    @Transactional(readOnly = true)
    public AulaResponse buscar(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aula aula = buscarAula(id, escolaId, professorAtual(escolaId));
        return AulaResponse.de(aula, montarChamada(aula, escolaId));
    }

    /**
     * Registra a aula com a chamada, tudo ou nada.
     *
     * @throws IllegalArgumentException data futura, aluno repetido, aluno sem
     *                                  matricula ativa na turma ou aluno
     *                                  ativo fora da chamada
     */
    @Transactional
    public AulaResponse registrar(AulaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = professorAtual(escolaId);
        exigirAlocacao(professor, escolaId, request.turmaId(), request.disciplinaId());
        if (request.data().isAfter(hoje())) {
            throw new IllegalArgumentException("A data da aula nao pode ser futura");
        }
        Map<UUID, MatriculaTurma> ativos = matriculasAtivas(escolaId, request.turmaId());
        validarChamada(request.chamada(), ativos, Set.of());

        Aula aula = aulaRepository.saveAndFlush(Aula.builder()
                .escolaId(escolaId)
                .turma(turmaRepository.getReferenceById(request.turmaId()))
                .disciplina(disciplinaRepository.getReferenceById(request.disciplinaId()))
                .professor(professor)
                .data(request.data())
                .conteudo(request.conteudo().trim())
                .build());
        frequenciaRepository.saveAll(request.chamada().stream()
                .map(item -> Frequencia.builder()
                        .escolaId(escolaId)
                        .aula(aula)
                        .aluno(ativos.get(item.alunoId()).getAluno())
                        .presente(item.presente())
                        .build())
                .toList());
        frequenciaRepository.flush();
        return resposta(aula.getId(), escolaId);
    }

    /**
     * Corrige o conteudo e a chamada de uma aula.
     *
     * @throws IllegalArgumentException aluno repetido, aluno sem matricula
     *                                  ativa na turma (e sem presenca ja
     *                                  registrada nesta aula) ou aluno ativo
     *                                  fora da chamada
     */
    @Transactional
    public AulaResponse atualizar(UUID id, AtualizarAulaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aula aula = buscarAula(id, escolaId, professorAtual(escolaId));

        Map<UUID, MatriculaTurma> ativos = matriculasAtivas(escolaId, aula.getTurma().getId());
        Map<UUID, Frequencia> registradas = frequenciaRepository.findAllByEscolaIdAndAulaId(escolaId, id).stream()
                .collect(Collectors.toMap(f -> f.getAluno().getId(), Function.identity()));
        validarChamada(request.chamada(), ativos, registradas.keySet());

        Instant agora = clock.instant();
        aula.setConteudo(request.conteudo().trim());
        aula.setAtualizadoEm(agora);
        aulaRepository.save(aula);

        List<Frequencia> novas = new ArrayList<>();
        for (ChamadaItem item : request.chamada()) {
            Frequencia existente = registradas.get(item.alunoId());
            if (existente == null) {
                novas.add(Frequencia.builder()
                        .escolaId(escolaId)
                        .aula(aula)
                        .aluno(ativos.get(item.alunoId()).getAluno())
                        .presente(item.presente())
                        .build());
            } else if (existente.isPresente() != item.presente()) {
                existente.setPresente(item.presente());
                existente.setAtualizadoEm(agora);
            }
        }
        frequenciaRepository.saveAll(novas);
        frequenciaRepository.flush();
        return resposta(id, escolaId);
    }

    /**
     * A chamada vale se traz cada aluno uma vez, so alunos da turma (ativos, ou
     * com presenca ja registrada nesta aula) e todos os ativos.
     */
    private static void validarChamada(List<ChamadaItem> chamada, Map<UUID, MatriculaTurma> ativos,
            Set<UUID> jaRegistrados) {
        Set<UUID> enviados = new HashSet<>();
        for (ChamadaItem item : chamada) {
            if (!enviados.add(item.alunoId())) {
                throw new IllegalArgumentException("Aluno repetido na chamada");
            }
            if (!ativos.containsKey(item.alunoId()) && !jaRegistrados.contains(item.alunoId())) {
                throw new IllegalArgumentException("Aluno sem matricula ativa na turma da aula");
            }
        }
        long ausentes = ativos.keySet().stream().filter(aluno -> !enviados.contains(aluno)).count();
        if (ausentes > 0) {
            throw new IllegalArgumentException(
                    "Chamada incompleta: %d aluno(s) com matricula ativa na turma ficaram fora da lista"
                            .formatted(ausentes));
        }
    }

    private AulaResponse resposta(UUID aulaId, UUID escolaId) {
        // Relida com o EntityGraph para devolver nome da turma e da disciplina.
        Aula aula = aulaRepository.findByIdAndEscolaId(aulaId, escolaId).orElseThrow(AulaService::naoEncontrada);
        return AulaResponse.de(aula, montarChamada(aula, escolaId));
    }

    /** A chamada da aula, pelo nome do aluno: quem tem presenca registrada, esteja ou nao ainda na turma. */
    private List<FrequenciaAlunoResponse> montarChamada(Aula aula, UUID escolaId) {
        List<Frequencia> frequencias = frequenciaRepository.findAllByEscolaIdAndAulaId(escolaId, aula.getId());
        if (frequencias.isEmpty()) {
            return List.of();
        }
        Map<UUID, MatriculaTurma> matriculasPorAluno = matriculaRepository
                .findAllByEscolaIdAndTurmaId(escolaId, aula.getTurma().getId()).stream()
                .collect(Collectors.toMap(m -> m.getAluno().getId(), Function.identity()));
        List<UUID> pessoaIds = frequencias.stream().map(f -> f.getAluno().getPessoaId()).distinct().toList();
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Function.identity()));

        return frequencias.stream()
                .map(f -> {
                    Pessoa pessoa = pessoasPorId.get(f.getAluno().getPessoaId());
                    MatriculaTurma matricula = matriculasPorAluno.get(f.getAluno().getId());
                    return new FrequenciaAlunoResponse(f.getAluno().getId(), pessoa != null ? pessoa.getNome() : null,
                            f.getAluno().getMatricula(), matricula != null ? matricula.getStatus() : null,
                            f.isPresente());
                })
                .sorted(Comparator.comparing(FrequenciaAlunoResponse::alunoNome, Paginacao.ORDEM_DE_NOME))
                .toList();
    }

    private Map<UUID, MatriculaTurma> matriculasAtivas(UUID escolaId, Long turmaId) {
        return matriculaRepository.findAllByEscolaIdAndTurmaId(escolaId, turmaId).stream()
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .collect(Collectors.toMap(m -> m.getAluno().getId(), Function.identity()));
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }

    /** Professor dono do login. 404 se o login nao e de um professor da escola. */
    private Professor professorAtual(UUID escolaId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        return professorRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor nao encontrado"));
    }

    private void exigirAlocacao(Professor professor, UUID escolaId, Long turmaId, Long disciplinaId) {
        if (turmaId == null || disciplinaId == null || !alocacaoRepository
                .existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(escolaId, professor.getId(), turmaId,
                        disciplinaId)) {
            throw new RecursoNaoEncontradoException("Turma ou disciplina nao alocada ao professor");
        }
    }

    /** Aula da escola, de uma turma/disciplina em que o professor esta alocado. */
    private Aula buscarAula(UUID id, UUID escolaId, Professor professor) {
        Aula aula = aulaRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(AulaService::naoEncontrada);
        if (!alocacaoRepository.existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(escolaId, professor.getId(),
                aula.getTurma().getId(), aula.getDisciplina().getId())) {
            throw naoEncontrada();
        }
        return aula;
    }

    private static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("Aula nao encontrada");
    }
}
