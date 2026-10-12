package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.AtualizarCobrancaRequest;
import br.pucminas.eduka.backend.dto.CobrancaRequest;
import br.pucminas.eduka.backend.dto.CobrancaResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Cobrancas dos alunos, com isolamento por escola (RF05, RNF03): cobranca,
 * aluno, matricula e responsavel sao sempre buscados com a escola do token, e
 * os de outra escola respondem 404.
 *
 * <p>
 * Regras:
 * </p>
 * <ul>
 * <li>Toda cobranca nasce PENDENTE.</li>
 * <li>A matricula, se informada, precisa ser do aluno; o responsavel, se
 * informado, precisa estar ligado ao aluno.</li>
 * <li>So cobranca em aberto (pendente ou em atraso) e editada ou cancelada;
 * PAGA e CANCELADA sao finais.</li>
 * <li>Cancelar nao apaga: a cobranca fica no historico como CANCELADA.</li>
 * </ul>
 */
@Service
public class CobrancaService {

    /** "Em atraso" segue o calendario da escola, nao o de UTC. */
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final CobrancaRepository cobrancaRepository;
    private final AlunoRepository alunoRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final AlunoResponsavelRepository alunoResponsavelRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public CobrancaService(CobrancaRepository cobrancaRepository, AlunoRepository alunoRepository,
            MatriculaTurmaRepository matriculaRepository, AlunoResponsavelRepository alunoResponsavelRepository,
            PessoaRepository pessoaRepository, IsolamentoEscola isolamentoEscola, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.alunoRepository = alunoRepository;
        this.matriculaRepository = matriculaRepository;
        this.alunoResponsavelRepository = alunoResponsavelRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    /**
     * Cobrancas da escola, opcionalmente filtradas por aluno, situacao e
     * intervalo de vencimento (inclusivo). Filtro de aluno de outra escola
     * devolve lista vazia. Ordenado pelo vencimento.
     */
    @Transactional(readOnly = true)
    public List<CobrancaResponse> listar(UUID alunoId, StatusCobranca status, LocalDate vencimentoDe,
            LocalDate vencimentoAte) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        LocalDate hoje = hoje();
        List<Cobranca> cobrancas = alunoId != null
                ? cobrancaRepository.findAllByEscolaIdAndAlunoId(escolaId, alunoId)
                : cobrancaRepository.findAllByEscolaId(escolaId);

        List<Cobranca> filtradas = cobrancas.stream()
                .filter(c -> status == null
                        || StatusCobranca.efetivo(c.getStatus(), c.getDataVencimento(), hoje) == status)
                .filter(c -> vencimentoDe == null || !c.getDataVencimento().isBefore(vencimentoDe))
                .filter(c -> vencimentoAte == null || !c.getDataVencimento().isAfter(vencimentoAte))
                .sorted(Comparator.comparing(Cobranca::getDataVencimento))
                .toList();
        return paraRespostas(filtradas, escolaId, hoje);
    }

    @Transactional(readOnly = true)
    public CobrancaResponse buscar(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return paraResposta(buscarCobranca(id, escolaId), escolaId);
    }

    /**
     * Lanca a cobranca como PENDENTE.
     *
     * @throws IllegalArgumentException se a matricula ou o responsavel nao
     *                                  forem do aluno
     */
    @Transactional
    public CobrancaResponse criar(CobrancaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(request.alunoId(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));

        Cobranca cobranca = Cobranca.builder()
                .escolaId(escolaId)
                .aluno(aluno)
                .matricula(buscarMatriculaDoAluno(request.matriculaId(), aluno, escolaId))
                .responsavelId(exigirResponsavelDoAluno(request.responsavelId(), aluno, escolaId))
                .descricao(request.descricao().trim())
                .valor(request.valor())
                .dataVencimento(request.dataVencimento())
                .status(StatusCobranca.PENDENTE)
                .build();
        return paraResposta(cobrancaRepository.saveAndFlush(cobranca), escolaId);
    }

    /**
     * Substitui os dados editaveis de uma cobranca em aberto.
     *
     * @throws IllegalArgumentException se a cobranca ja foi paga ou cancelada,
     *                                  ou se a matricula ou o responsavel nao
     *                                  forem do aluno
     */
    @Transactional
    public CobrancaResponse atualizar(UUID id, AtualizarCobrancaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Cobranca cobranca = buscarCobranca(id, escolaId);
        exigirEmAberto(cobranca, "editada");
        Aluno aluno = cobranca.getAluno();

        cobranca.setMatricula(buscarMatriculaDoAluno(request.matriculaId(), aluno, escolaId));
        cobranca.setResponsavelId(exigirResponsavelDoAluno(request.responsavelId(), aluno, escolaId));
        cobranca.setDescricao(request.descricao().trim());
        cobranca.setValor(request.valor());
        cobranca.setDataVencimento(request.dataVencimento());
        cobranca.setAtualizadoEm(clock.instant());
        return paraResposta(cobrancaRepository.saveAndFlush(cobranca), escolaId);
    }

    /**
     * Cancela uma cobranca em aberto.
     *
     * @throws IllegalArgumentException se a cobranca ja foi paga ou cancelada
     */
    @Transactional
    public CobrancaResponse cancelar(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Cobranca cobranca = buscarCobranca(id, escolaId);
        exigirEmAberto(cobranca, "cancelada");

        cobranca.setStatus(StatusCobranca.CANCELADA);
        cobranca.setCanceladaEm(clock.instant());
        cobranca.setAtualizadoEm(clock.instant());
        return paraResposta(cobrancaRepository.saveAndFlush(cobranca), escolaId);
    }

    private Cobranca buscarCobranca(UUID id, UUID escolaId) {
        return cobrancaRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cobranca nao encontrada"));
    }

    private static void exigirEmAberto(Cobranca cobranca, String acao) {
        if (!cobranca.getStatus().emAberto()) {
            throw new IllegalArgumentException(
                    "Cobranca %s nao pode ser %s".formatted(cobranca.getStatus(), acao));
        }
    }

    private MatriculaTurma buscarMatriculaDoAluno(UUID matriculaId, Aluno aluno, UUID escolaId) {
        if (matriculaId == null) {
            return null;
        }
        MatriculaTurma matricula = matriculaRepository.findByIdAndEscolaId(matriculaId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matricula nao encontrada"));
        if (!matricula.getAluno().getId().equals(aluno.getId())) {
            throw new IllegalArgumentException("Matricula nao pertence ao aluno");
        }
        return matricula;
    }

    /** Devolve o proprio id, depois de conferir a escola e o vinculo com o aluno. */
    private UUID exigirResponsavelDoAluno(UUID responsavelId, Aluno aluno, UUID escolaId) {
        if (responsavelId == null) {
            return null;
        }
        pessoaRepository.findByIdAndEscolaId(responsavelId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsavel nao encontrado"));
        if (!alunoResponsavelRepository.existsById(new AlunoResponsavelId(aluno.getId(), responsavelId))) {
            throw new IllegalArgumentException("Responsavel nao esta vinculado ao aluno");
        }
        return responsavelId;
    }

    private CobrancaResponse paraResposta(Cobranca cobranca, UUID escolaId) {
        return paraRespostas(List.of(cobranca), escolaId, hoje()).getFirst();
    }

    /** Busca de uma vez as pessoas (alunos e responsaveis) que dao nome as cobrancas. */
    private List<CobrancaResponse> paraRespostas(List<Cobranca> cobrancas, UUID escolaId, LocalDate hoje) {
        if (cobrancas.isEmpty()) {
            return List.of();
        }
        List<UUID> pessoaIds = cobrancas.stream()
                .flatMap(c -> Stream.of(c.getAluno().getPessoaId(), c.getResponsavelId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Function.identity()));
        return cobrancas.stream()
                .map(c -> CobrancaResponse.de(c, pessoasPorId.get(c.getAluno().getPessoaId()),
                        c.getResponsavelId() != null ? pessoasPorId.get(c.getResponsavelId()) : null, hoje))
                .toList();
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }
}
