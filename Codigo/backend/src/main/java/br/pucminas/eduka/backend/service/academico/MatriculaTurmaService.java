package br.pucminas.eduka.backend.service.academico;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.academico.AtualizarMatriculaRequest;
import br.pucminas.eduka.backend.dto.academico.MatriculaTurmaRequest;
import br.pucminas.eduka.backend.dto.academico.MatriculaTurmaResponse;
import br.pucminas.eduka.backend.dto.academico.ResumoMatriculasResponse;
import br.pucminas.eduka.backend.dto.comum.Ordenacao;
import br.pucminas.eduka.backend.dto.comum.PaginaComResumoResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.exception.academico.MatriculaJaExisteException;
import br.pucminas.eduka.backend.exception.academico.TurmaSemVagasException;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;
import br.pucminas.eduka.backend.service.pessoas.AlunoService;

/**
 * Matricula de alunos em turmas, com isolamento por escola (RF05, RNF03):
 * aluno, turma e matricula sao sempre buscados com a escola do token, e os de
 * outra escola respondem 404.
 *
 * <p>
 * Regras:
 * </p>
 * <ul>
 * <li>So turma ativa recebe matricula, transferencia ou reativacao.</li>
 * <li>Um registro por aluno e turma (409 se ja houver, em qualquer
 * situacao).</li>
 * <li>So matricula ATIVA ocupa vaga; ocupar vaga numa turma cheia responde
 * 409. A turma fica travada durante a contagem, para duas matriculas
 * simultaneas nao passarem do limite.</li>
 * <li>A situacao segue as transicoes de {@link StatusMatricula}; CANCELADA e
 * CONCLUIDA sao finais.</li>
 * <li>Transferir de turma so vale para matricula que fica ATIVA.</li>
 * </ul>
 */
@Service
public class MatriculaTurmaService {

    /** A data da matricula e a do calendario da escola, nao a de UTC. */
    /** Fuso das datas da escola; o servico academico usa o mesmo. */
    static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final MatriculaTurmaRepository matriculaRepository;
    private final AlunoRepository alunoRepository;
    private final TurmaRepository turmaRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;
    private final AuditoriaService auditoriaService;

    public MatriculaTurmaService(MatriculaTurmaRepository matriculaRepository, AlunoRepository alunoRepository,
            TurmaRepository turmaRepository, PessoaRepository pessoaRepository, IsolamentoEscola isolamentoEscola,
            Clock clock, AuditoriaService auditoriaService) {
        this.matriculaRepository = matriculaRepository;
        this.alunoRepository = alunoRepository;
        this.turmaRepository = turmaRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Pagina das matriculas, com os mesmos filtros de {@link #listar}, e o
     * resumo da turma (ou do aluno) sem o filtro de situacao: os contadores
     * nao mudam ao trocar de filtro. O nome do aluno e cifrado, entao a ordem
     * por ele e feita em memoria, e so a pagina vai na resposta.
     */
    @Transactional(readOnly = true)
    public PaginaComResumoResponse<MatriculaTurmaResponse, ResumoMatriculasResponse> listarPaginado(Long turmaId,
            UUID alunoId, StatusMatricula status, Paginacao paginacao, Ordenacao.Matriculas ordenarPor,
            Direction direcao) {
        Comparator<MatriculaTurmaResponse> campo = switch (ordenarPor) {
            case ALUNO -> Comparator.comparing(MatriculaTurmaResponse::alunoNome, Paginacao.ORDEM_DE_NOME);
            case DATA_MATRICULA -> Comparator.comparing(MatriculaTurmaResponse::dataMatricula,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<MatriculaTurma> registros = daConsulta(escolaId, turmaId, alunoId);

        Long paraMatricular = null;
        if (turmaId != null && alunoId == null) {
            // Um registro por aluno e turma, em qualquer situacao; anonimizado e
            // inativo nao recebem matricula, entao ficam fora das duas contas.
            long naTurma = registros.stream().map(MatriculaTurma::getAluno)
                    .filter(a -> !a.isAnonimizado() && a.isAtivo())
                    .map(Aluno::getId).distinct().count();
            paraMatricular = Math.max(0,
                    alunoRepository.countByEscolaIdAndAnonimizadoEmIsNullAndAtivoTrue(escolaId) - naTurma);
        }
        ResumoMatriculasResponse resumo = ResumoMatriculasResponse
                .de(registros.stream().map(MatriculaTurma::getStatus).toList(), paraMatricular);

        List<MatriculaTurmaResponse> ordenadas = responder(escolaId,
                registros.stream().filter(m -> status == null || m.getStatus() == status).toList()).stream()
                .sorted(Ordenacao.emMemoria(campo, Ordenacao.ou(direcao, Direction.ASC),
                        Comparator.comparing(MatriculaTurmaResponse::id)))
                .toList();
        return PaginaComResumoResponse.de(PaginaResponse.recortar(ordenadas, paginacao), resumo);
    }

    /**
     * Matriculas da escola, opcionalmente filtradas por turma, aluno e
     * situacao. Filtro de turma ou aluno de outra escola devolve lista vazia.
     * Ordenado pelo nome do aluno.
     */
    @Transactional(readOnly = true)
    public List<MatriculaTurmaResponse> listar(Long turmaId, UUID alunoId, StatusMatricula status) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return responder(escolaId, daConsulta(escolaId, turmaId, alunoId).stream()
                .filter(m -> status == null || m.getStatus() == status)
                .toList());
    }

    /** Registros da escola, da turma e/ou do aluno pedidos, em qualquer situacao. */
    private List<MatriculaTurma> daConsulta(UUID escolaId, Long turmaId, UUID alunoId) {
        List<MatriculaTurma> matriculas;
        if (turmaId != null) {
            matriculas = matriculaRepository.findAllByEscolaIdAndTurmaId(escolaId, turmaId);
        } else if (alunoId != null) {
            matriculas = matriculaRepository.findAllByEscolaIdAndAlunoId(escolaId, alunoId);
        } else {
            matriculas = matriculaRepository.findAllByEscolaId(escolaId);
        }
        return matriculas.stream()
                .filter(m -> alunoId == null || m.getAluno().getId().equals(alunoId))
                .toList();
    }

    /** Monta a resposta com o nome de cada aluno, em ordem de nome. */
    private List<MatriculaTurmaResponse> responder(UUID escolaId, List<MatriculaTurma> filtradas) {
        if (filtradas.isEmpty()) {
            return List.of();
        }

        List<UUID> pessoaIds = filtradas.stream().map(m -> m.getAluno().getPessoaId()).distinct().toList();
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Function.identity()));
        return filtradas.stream()
                .map(m -> MatriculaTurmaResponse.de(m, pessoasPorId.get(m.getAluno().getPessoaId())))
                .sorted(Comparator.comparing(MatriculaTurmaResponse::alunoNome,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MatriculaTurmaResponse buscar(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return paraResposta(buscarMatricula(id, escolaId), escolaId);
    }

    /**
     * Matricula o aluno na turma, como ATIVA.
     *
     * @throws MatriculaJaExisteException se o aluno ja tem matricula na turma
     * @throws TurmaSemVagasException     se a turma esta cheia
     */
    @Transactional
    public MatriculaTurmaResponse matricular(MatriculaTurmaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(request.alunoId(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
        AlunoService.exigirNaoAnonimizado(aluno);
        AlunoService.exigirAtivo(aluno);
        Turma turma = buscarTurmaParaOcupar(request.turmaId(), escolaId);
        if (matriculaRepository.existsByEscolaIdAndAlunoIdAndTurmaId(escolaId, aluno.getId(), turma.getId())) {
            throw new MatriculaJaExisteException();
        }
        exigirVaga(turma, escolaId);

        MatriculaTurma matricula = MatriculaTurma.builder()
                .escolaId(escolaId)
                .aluno(aluno)
                .turma(turma)
                .status(StatusMatricula.ATIVA)
                .dataMatricula(request.dataMatricula() != null ? request.dataMatricula() : hoje())
                .observacao(opcional(request.observacao()))
                .build();
        MatriculaTurma salva = salvar(matricula);
        auditoriaService.registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.MATRICULA, salva.getId());
        return paraResposta(salva, escolaId);
    }

    /**
     * Atualiza situacao, turma e/ou observacao. So o que vier no request muda.
     *
     * @throws IllegalArgumentException   transicao de situacao invalida, ou
     *                                    transferencia de matricula nao ativa
     * @throws MatriculaJaExisteException se o aluno ja tem matricula na turma
     *                                    de destino
     * @throws TurmaSemVagasException     se reativar ou transferir para turma
     *                                    cheia
     */
    @Transactional
    public MatriculaTurmaResponse atualizar(UUID id, AtualizarMatriculaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        MatriculaTurma matricula = buscarMatricula(id, escolaId);
        StatusMatricula atual = matricula.getStatus();
        StatusMatricula novo = request.status() != null ? request.status() : atual;

        if (novo != atual && !atual.podeMudarPara(novo)) {
            throw new IllegalArgumentException("Matricula %s nao pode passar para %s".formatted(atual, novo));
        }
        if (atual != StatusMatricula.ATIVA && novo == StatusMatricula.ATIVA) {
            // Aluno inativo nao volta a ter matricula ativa sem ser reativado antes.
            AlunoService.exigirAtivo(matricula.getAluno());
        }

        boolean transferindo = request.turmaId() != null && !request.turmaId().equals(matricula.getTurma().getId());
        if (transferindo) {
            if (novo != StatusMatricula.ATIVA) {
                throw new IllegalArgumentException("So matricula ativa pode ser transferida de turma");
            }
            Turma destino = buscarTurmaParaOcupar(request.turmaId(), escolaId);
            if (matriculaRepository.existsByEscolaIdAndAlunoIdAndTurmaId(escolaId, matricula.getAluno().getId(),
                    destino.getId())) {
                throw new MatriculaJaExisteException();
            }
            exigirVaga(destino, escolaId);
            matricula.setTurma(destino);
        } else if (atual != StatusMatricula.ATIVA && novo == StatusMatricula.ATIVA) {
            // Reativacao: volta a ocupar vaga na mesma turma.
            exigirVaga(buscarTurmaParaOcupar(matricula.getTurma().getId(), escolaId), escolaId);
        }

        matricula.setStatus(novo);
        if (request.observacao() != null) {
            matricula.setObservacao(opcional(request.observacao()));
        }
        matricula.setAtualizadoEm(clock.instant());
        MatriculaTurma salva = salvar(matricula);
        // Cancelar fica como CANCELAR; trancar, concluir, reativar e transferir sao edicoes.
        boolean cancelou = novo == StatusMatricula.CANCELADA && atual != StatusMatricula.CANCELADA;
        auditoriaService.registrar(cancelou ? OperacaoAuditoria.CANCELAR : OperacaoAuditoria.EDITAR,
                EntidadeAuditada.MATRICULA, salva.getId());
        return paraResposta(salva, escolaId);
    }

    private MatriculaTurma buscarMatricula(UUID id, UUID escolaId) {
        return matriculaRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matricula nao encontrada"));
    }

    /** Turma onde a matricula vai ocupar vaga: travada e obrigatoriamente ativa. */
    private Turma buscarTurmaParaOcupar(Long turmaId, UUID escolaId) {
        Turma turma = turmaRepository.findComLockByIdAndEscolaId(turmaId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma nao encontrada"));
        if (!Boolean.TRUE.equals(turma.getAtiva())) {
            throw new IllegalArgumentException("Turma inativa nao recebe matricula");
        }
        // IU09: periodo encerrado nao recebe matricula; planejado, so depois de Abrir matriculas.
        PeriodoLetivo periodo = turma.getPeriodoLetivo();
        if (periodo.getEncerradoEm() != null) {
            throw new IllegalArgumentException("Periodo letivo encerrado nao recebe matricula");
        }
        if (hoje().isBefore(periodo.getDataInicio()) && !periodo.isMatriculasAbertas()) {
            throw new IllegalArgumentException("As matriculas deste periodo letivo ainda nao foram abertas");
        }
        return turma;
    }

    private void exigirVaga(Turma turma, UUID escolaId) {
        long ocupadas = matriculaRepository.countByEscolaIdAndTurmaIdAndStatus(escolaId, turma.getId(),
                StatusMatricula.ATIVA);
        if (ocupadas >= turma.getVagas()) {
            throw new TurmaSemVagasException();
        }
    }

    /**
     * flush ja aqui: a UNIQUE (aluno, turma) do banco e a ultima barreira
     * contra matricula duplicada, e precisa estourar dentro do try.
     */
    private MatriculaTurma salvar(MatriculaTurma matricula) {
        try {
            return matriculaRepository.saveAndFlush(matricula);
        } catch (DataIntegrityViolationException e) {
            throw new MatriculaJaExisteException();
        }
    }

    private MatriculaTurmaResponse paraResposta(MatriculaTurma matricula, UUID escolaId) {
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(matricula.getAluno().getPessoaId(), escolaId)
                .orElse(null);
        return MatriculaTurmaResponse.de(matricula, pessoa);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }

    /** Observacao vazia vira null, para nao gravar string vazia. */
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
