package br.pucminas.eduka.backend.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Avaliacao;
import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Nota;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.AtualizarAvaliacaoRequest;
import br.pucminas.eduka.backend.dto.AvaliacaoRequest;
import br.pucminas.eduka.backend.dto.AvaliacaoResponse;
import br.pucminas.eduka.backend.dto.LancarNotasRequest;
import br.pucminas.eduka.backend.dto.LancarNotasRequest.NotaLancamento;
import br.pucminas.eduka.backend.dto.NotaAlunoResponse;
import br.pucminas.eduka.backend.exception.AvaliacaoComNotasException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.NotaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Avaliacoes e notas, pelo professor autenticado (portal do professor).
 *
 * <p>
 * O professor e a escola saem do token. Ele so enxerga e altera avaliacoes
 * das disciplinas em que esta alocado hoje (ver
 * {@link AlocacaoProfessorService}); fora disso, a turma/disciplina responde
 * 404, como se nao existisse. Assim, quando a secretaria troca o professor de
 * uma disciplina, as avaliacoes passam para o novo professor.
 * </p>
 *
 * <p>
 * Regras das notas:
 * </p>
 * <ul>
 * <li>So aluno com matricula ATIVA na turma recebe nota.</li>
 * <li>A nota vai de 0 ate o valor da avaliacao; reduzir o valor abaixo de uma
 * nota ja lancada e recusado.</li>
 * <li>Relancar substitui a nota; nota nula apaga.</li>
 * <li>Avaliacao com nota lancada nao e excluida (409).</li>
 * </ul>
 */
@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final NotaRepository notaRepository;
    private final ProfessorRepository professorRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final TurmaRepository turmaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final AuditoriaService auditoriaService;

    public AvaliacaoService(AvaliacaoRepository avaliacaoRepository, NotaRepository notaRepository,
            ProfessorRepository professorRepository, AlocacaoProfessorRepository alocacaoRepository,
            TurmaRepository turmaRepository, DisciplinaRepository disciplinaRepository,
            MatriculaTurmaRepository matriculaRepository, PessoaRepository pessoaRepository,
            IsolamentoEscola isolamentoEscola, AuditoriaService auditoriaService) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.notaRepository = notaRepository;
        this.professorRepository = professorRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.turmaRepository = turmaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.matriculaRepository = matriculaRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.auditoriaService = auditoriaService;
    }

    /** Avaliacoes da disciplina na turma, por data e titulo. */
    @Transactional(readOnly = true)
    public List<AvaliacaoResponse> listar(Long turmaId, Long disciplinaId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirAlocacao(professorAtual(escolaId), escolaId, turmaId, disciplinaId);

        List<Avaliacao> avaliacoes = avaliacaoRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaId(escolaId,
                turmaId, disciplinaId);
        if (avaliacoes.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> notasPorAvaliacao = notaRepository
                .contarPorAvaliacao(escolaId, avaliacoes.stream().map(Avaliacao::getId).toList()).stream()
                .collect(Collectors.toMap(NotaRepository.NotasPorAvaliacao::getAvaliacaoId,
                        NotaRepository.NotasPorAvaliacao::getTotal));
        return avaliacoes.stream()
                .sorted(Comparator.comparing(Avaliacao::getData)
                        .thenComparing(Avaliacao::getTitulo, String.CASE_INSENSITIVE_ORDER))
                .map(a -> AvaliacaoResponse.de(a, notasPorAvaliacao.getOrDefault(a.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public AvaliacaoResponse buscar(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Avaliacao avaliacao = buscarAvaliacao(id, escolaId, professorAtual(escolaId));
        return comContagem(avaliacao, escolaId);
    }

    @Transactional
    public AvaliacaoResponse criar(AvaliacaoRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = professorAtual(escolaId);
        exigirAlocacao(professor, escolaId, request.turmaId(), request.disciplinaId());

        Avaliacao avaliacao = Avaliacao.builder()
                .escolaId(escolaId)
                .turma(turmaRepository.getReferenceById(request.turmaId()))
                .disciplina(disciplinaRepository.getReferenceById(request.disciplinaId()))
                .professor(professor)
                .tipo(request.tipo())
                .titulo(request.titulo().trim())
                .descricao(opcional(request.descricao()))
                .data(request.data())
                .valor(request.valor())
                .build();
        Avaliacao salva = avaliacaoRepository.saveAndFlush(avaliacao);
        auditoriaService.registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.AVALIACAO, salva.getId());
        // Relida com o EntityGraph para devolver nome da turma e da disciplina.
        return AvaliacaoResponse.de(avaliacaoRepository.findByIdAndEscolaId(salva.getId(), escolaId)
                .orElseThrow(AvaliacaoService::naoEncontrada), 0);
    }

    /**
     * @throws IllegalArgumentException se o novo valor fica abaixo de alguma
     *                                  nota ja lancada
     */
    @Transactional
    public AvaliacaoResponse atualizar(UUID id, AtualizarAvaliacaoRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Avaliacao avaliacao = buscarAvaliacao(id, escolaId, professorAtual(escolaId));

        boolean notaAcimaDoNovoValor = notaRepository.findAllByEscolaIdAndAvaliacaoId(escolaId, id).stream()
                .anyMatch(nota -> nota.getValor().compareTo(request.valor()) > 0);
        if (notaAcimaDoNovoValor) {
            throw new IllegalArgumentException("Ha notas lancadas maiores que o novo valor da avaliacao");
        }

        avaliacao.setTipo(request.tipo());
        avaliacao.setTitulo(request.titulo().trim());
        avaliacao.setDescricao(opcional(request.descricao()));
        avaliacao.setData(request.data());
        avaliacao.setValor(request.valor());
        avaliacao.setAtualizadoEm(Instant.now());
        auditoriaService.registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.AVALIACAO, avaliacao.getId());
        return comContagem(avaliacaoRepository.save(avaliacao), escolaId);
    }

    /** @throws AvaliacaoComNotasException se ja ha nota lancada */
    @Transactional
    public void excluir(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Avaliacao avaliacao = buscarAvaliacao(id, escolaId, professorAtual(escolaId));
        if (notaRepository.existsByEscolaIdAndAvaliacaoId(escolaId, id)) {
            throw new AvaliacaoComNotasException();
        }
        avaliacaoRepository.delete(avaliacao);
        auditoriaService.registrar(OperacaoAuditoria.EXCLUIR, EntidadeAuditada.AVALIACAO, avaliacao.getId());
    }

    /**
     * Diario de notas da avaliacao: todo aluno com matricula ATIVA na turma,
     * com a nota (nula se ainda nao lancada), mais quem ja tem nota mas saiu
     * da turma ou trancou. Ordenado pelo nome.
     */
    @Transactional(readOnly = true)
    public List<NotaAlunoResponse> listarNotas(UUID avaliacaoId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Avaliacao avaliacao = buscarAvaliacao(avaliacaoId, escolaId, professorAtual(escolaId));
        return montarNotas(avaliacao, escolaId);
    }

    /**
     * Lanca, corrige ou apaga (valor nulo) as notas enviadas. Tudo ou nada: um
     * item invalido recusa o lote inteiro.
     *
     * @throws IllegalArgumentException aluno repetido no lote, aluno sem
     *                                  matricula ativa na turma, ou nota
     *                                  acima do valor da avaliacao
     */
    @Transactional
    public List<NotaAlunoResponse> lancarNotas(UUID avaliacaoId, LancarNotasRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Avaliacao avaliacao = buscarAvaliacao(avaliacaoId, escolaId, professorAtual(escolaId));

        Set<UUID> vistos = new HashSet<>();
        for (NotaLancamento item : request.notas()) {
            if (!vistos.add(item.alunoId())) {
                throw new IllegalArgumentException("Aluno repetido no lancamento de notas");
            }
            if (item.valor() != null && item.valor().compareTo(avaliacao.getValor()) > 0) {
                throw new IllegalArgumentException(
                        "Nota maior que o valor da avaliacao (%s)".formatted(avaliacao.getValor().toPlainString()));
            }
        }

        Map<UUID, MatriculaTurma> ativasPorAluno = matriculaRepository
                .findAllByEscolaIdAndTurmaId(escolaId, avaliacao.getTurma().getId()).stream()
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .collect(Collectors.toMap(m -> m.getAluno().getId(), Function.identity()));
        Map<UUID, Nota> notasPorAluno = notasPorAluno(avaliacao, escolaId);

        for (NotaLancamento item : request.notas()) {
            MatriculaTurma matricula = ativasPorAluno.get(item.alunoId());
            if (matricula == null) {
                throw new IllegalArgumentException("Aluno sem matricula ativa na turma da avaliacao");
            }
            Nota existente = notasPorAluno.get(item.alunoId());
            // Uma linha no log por nota que mudou: reenviar o diario igual nao registra nada.
            if (item.valor() == null) {
                if (existente != null) {
                    notaRepository.delete(existente);
                    auditoriaService.registrar(OperacaoAuditoria.EXCLUIR, EntidadeAuditada.NOTA, existente.getId());
                }
                continue;
            }
            String observacao = opcional(item.observacao());
            if (existente == null) {
                Nota nova = Nota.builder()
                        .escolaId(escolaId)
                        .avaliacao(avaliacao)
                        .aluno(matricula.getAluno())
                        .valor(item.valor())
                        .observacao(observacao)
                        .build();
                // Nota nova e persistida nesta mesma instancia, que ja sai daqui com o id.
                notaRepository.save(nova);
                auditoriaService.registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.NOTA, nova.getId());
            } else if (existente.getValor().compareTo(item.valor()) != 0
                    || !Objects.equals(existente.getObservacao(), observacao)) {
                existente.setValor(item.valor());
                existente.setObservacao(observacao);
                existente.setAtualizadoEm(Instant.now());
                notaRepository.save(existente);
                auditoriaService.registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.NOTA, existente.getId());
            }
        }
        // Rascunho guarda sem publicar; Lancar notas publica a avaliacao inteira no boletim.
        if (!request.ehRascunho() && !avaliacao.isNotasPublicadas()) {
            auditoriaService.registrar(OperacaoAuditoria.PUBLICAR_NOTAS, EntidadeAuditada.AVALIACAO,
                    avaliacao.getId());
        }
        avaliacao.setNotasPublicadas(!request.ehRascunho());
        avaliacaoRepository.save(avaliacao);
        notaRepository.flush();
        return montarNotas(avaliacao, escolaId);
    }

    private List<NotaAlunoResponse> montarNotas(Avaliacao avaliacao, UUID escolaId) {
        Map<UUID, MatriculaTurma> matriculasPorAluno = matriculaRepository
                .findAllByEscolaIdAndTurmaId(escolaId, avaliacao.getTurma().getId()).stream()
                .collect(Collectors.toMap(m -> m.getAluno().getId(), Function.identity()));
        Map<UUID, Nota> notasPorAluno = notasPorAluno(avaliacao, escolaId);

        // Linhas: quem esta ATIVO na turma + quem ja tem nota (mesmo fora dela).
        Map<UUID, Aluno> alunos = new LinkedHashMap<>();
        matriculasPorAluno.values().stream()
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .forEach(m -> alunos.put(m.getAluno().getId(), m.getAluno()));
        notasPorAluno.values().forEach(n -> alunos.putIfAbsent(n.getAluno().getId(), n.getAluno()));
        if (alunos.isEmpty()) {
            return List.of();
        }

        Set<UUID> pessoaIds = alunos.values().stream().map(Aluno::getPessoaId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository.findAllByIdInAndEscolaId(List.copyOf(pessoaIds), escolaId)
                .stream().collect(Collectors.toMap(Pessoa::getId, Function.identity()));

        return alunos.values().stream()
                .map(aluno -> {
                    Pessoa pessoa = pessoasPorId.get(aluno.getPessoaId());
                    MatriculaTurma matricula = matriculasPorAluno.get(aluno.getId());
                    Nota nota = notasPorAluno.get(aluno.getId());
                    return new NotaAlunoResponse(aluno.getId(), pessoa != null ? pessoa.getNome() : null,
                            aluno.getMatricula(), matricula != null ? matricula.getStatus() : null,
                            nota != null ? nota.getValor() : null, nota != null ? nota.getObservacao() : null);
                })
                .sorted(Comparator.comparing(NotaAlunoResponse::alunoNome,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Map<UUID, Nota> notasPorAluno(Avaliacao avaliacao, UUID escolaId) {
        return notaRepository.findAllByEscolaIdAndAvaliacaoId(escolaId, avaliacao.getId()).stream()
                .collect(Collectors.toMap(n -> n.getAluno().getId(), Function.identity()));
    }

    private AvaliacaoResponse comContagem(Avaliacao avaliacao, UUID escolaId) {
        long notas = notaRepository.contarPorAvaliacao(escolaId, List.of(avaliacao.getId())).stream()
                .mapToLong(NotaRepository.NotasPorAvaliacao::getTotal).sum();
        return AvaliacaoResponse.de(avaliacao, notas);
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

    /** Avaliacao da escola, de uma turma/disciplina em que o professor esta alocado. */
    private Avaliacao buscarAvaliacao(UUID id, UUID escolaId, Professor professor) {
        Avaliacao avaliacao = avaliacaoRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(AvaliacaoService::naoEncontrada);
        if (!alocacaoRepository.existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(escolaId,
                professor.getId(), avaliacao.getTurma().getId(), avaliacao.getDisciplina().getId())) {
            throw naoEncontrada();
        }
        return avaliacao;
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("Avaliacao nao encontrada");
    }
}
