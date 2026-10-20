package br.pucminas.eduka.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.AtualizarCobrancaRequest;
import br.pucminas.eduka.backend.dto.CobrancaRequest;
import br.pucminas.eduka.backend.dto.CobrancaResponse;
import br.pucminas.eduka.backend.dto.HistoricoFinanceiroResponse;
import br.pucminas.eduka.backend.dto.PaginaCobrancasResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.CobrancaFiltro;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.CobrancaTotaisRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

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
 * PAGA e CANCELADA sao finais. Toda troca de situacao passa por
 * {@link StatusCobranca#podeMudarPara}.</li>
 * <li>Cancelar nao apaga: a cobranca fica no historico como CANCELADA.</li>
 * <li>A cobranca vencida vira EM_ATRASO pela rotina diaria
 * ({@link #marcarVencidasComoEmAtraso}); adiar o vencimento na edicao a
 * devolve para PENDENTE.</li>
 * <li>Criar, editar e cancelar ficam no LOG_AUDITORIA com o usuario do token
 * ({@link AuditoriaService}). A rotina de atraso nao tem autor e nao e
 * auditada.</li>
 * </ul>
 */
@Service
public class CobrancaService {

    /** "Em atraso" segue o calendario da escola, nao o de UTC. */
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    /** Com quantos dias de antecedencia sai o lembrete de vencimento. */
    static final int DIAS_LEMBRETE_VENCIMENTO = 3;

    static final int TAMANHO_MAXIMO_PAGINA = 100;

    /** O id desempata vencimentos iguais, para a pagina seguinte nao repetir nem pular cobranca. */
    private static final Sort ORDEM_DA_LISTAGEM = Sort.by("dataVencimento", "id");

    private final CobrancaRepository cobrancaRepository;
    private final AlunoRepository alunoRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final AlunoResponsavelRepository alunoResponsavelRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final AuditoriaService auditoriaService;
    private final AvisoCobrancaService avisoCobrancaService;
    private final Clock clock;

    public CobrancaService(CobrancaRepository cobrancaRepository, AlunoRepository alunoRepository,
            MatriculaTurmaRepository matriculaRepository, AlunoResponsavelRepository alunoResponsavelRepository,
            PessoaRepository pessoaRepository, IsolamentoEscola isolamentoEscola,
            AuditoriaService auditoriaService, AvisoCobrancaService avisoCobrancaService, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.alunoRepository = alunoRepository;
        this.matriculaRepository = matriculaRepository;
        this.alunoResponsavelRepository = alunoResponsavelRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.auditoriaService = auditoriaService;
        this.avisoCobrancaService = avisoCobrancaService;
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

    /**
     * Pagina das cobrancas da escola que passam no {@code filtro}, pelo
     * vencimento, com os totais por situacao (ver
     * {@link PaginaCobrancasResponse#totaisPorStatus}). Filtro de aluno ou de
     * periodo de outra escola devolve pagina vazia.
     *
     * @throws IllegalArgumentException se a pagina ou o tamanho estao fora dos
     *                                  limites, ou se o intervalo de
     *                                  vencimento esta invertido
     */
    @Transactional(readOnly = true)
    public PaginaCobrancasResponse listarPaginado(CobrancaFiltro filtro, int pagina, int tamanho) {
        if (pagina < 0) {
            throw new IllegalArgumentException("Pagina deve ser zero ou maior");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            throw new IllegalArgumentException("Tamanho deve ser de 1 a " + TAMANHO_MAXIMO_PAGINA);
        }
        if (filtro.vencimentoDe() != null && filtro.vencimentoAte() != null
                && filtro.vencimentoDe().isAfter(filtro.vencimentoAte())) {
            throw new IllegalArgumentException("vencimentoDe nao pode ser depois de vencimentoAte");
        }
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        LocalDate hoje = hoje();

        Page<Cobranca> resultado = cobrancaRepository.findAll(filtro.daEscola(escolaId, hoje),
                PageRequest.of(pagina, tamanho, ORDEM_DA_LISTAGEM));
        return new PaginaCobrancasResponse(paraRespostas(resultado.getContent(), escolaId, hoje), pagina, tamanho,
                resultado.getTotalElements(), resultado.getTotalPages(),
                totaisPorStatus(filtro.daEscolaEmQualquerStatus(escolaId), hoje));
    }

    @Transactional(readOnly = true)
    public CobrancaResponse buscar(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return paraResposta(buscarCobranca(id, escolaId), escolaId);
    }

    /**
     * Historico financeiro de um aluno da escola, para a gestao/financeiro.
     *
     * @throws RecursoNaoEncontradoException se o aluno nao e da escola
     */
    @Transactional(readOnly = true)
    public HistoricoFinanceiroResponse historicoDoAluno(UUID alunoId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(alunoId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
        return historico(aluno, escolaId);
    }

    /**
     * Historico financeiro do aluno dono do login (portal do aluno). O aluno
     * sai do token: nao ha como pedir o historico de outro.
     *
     * @throws RecursoNaoEncontradoException se o login nao e de um aluno da escola
     */
    @Transactional(readOnly = true)
    public HistoricoFinanceiroResponse meuHistorico() {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        Aluno aluno = alunoRepository.findByUsuarioIdAndEscolaId(usuario.id(), usuario.escolaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
        return historico(aluno, usuario.escolaId());
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
                .lembreteVencimentoEm(dispensaLembrete(request.dataVencimento()))
                .build();
        CobrancaResponse resposta = salvarEAuditar(cobranca, OperacaoAuditoria.CRIAR, escolaId);
        avisoCobrancaService.publicar(AvisoCobranca.Tipo.COBRANCA_CRIADA, CobrancaParaAviso.de(cobranca));
        return resposta;
    }

    /**
     * Substitui os dados editaveis de uma cobranca em aberto. A situacao
     * acompanha o novo vencimento: adiar tira do atraso, antecipar para o
     * passado poe em atraso.
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
        if (!request.dataVencimento().equals(cobranca.getDataVencimento())) {
            // Novo vencimento, novo lembrete.
            cobranca.setLembreteVencimentoEm(null);
        }
        cobranca.setDataVencimento(request.dataVencimento());
        mudarStatus(cobranca, StatusCobranca.emAbertoCom(request.dataVencimento(), hoje()));
        cobranca.setAtualizadoEm(clock.instant());
        return salvarEAuditar(cobranca, OperacaoAuditoria.EDITAR, escolaId);
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

        mudarStatus(cobranca, StatusCobranca.CANCELADA);
        cobranca.setCanceladaEm(clock.instant());
        cobranca.setAtualizadoEm(clock.instant());
        return salvarEAuditar(cobranca, OperacaoAuditoria.CANCELAR, escolaId);
    }

    /**
     * Baixa manual: marca a cobranca em aberto como PAGA e avisa quem paga.
     * Um futuro retorno de gateway de pagamento deve passar por aqui.
     *
     * @throws IllegalArgumentException se a cobranca ja foi paga ou cancelada
     */
    @Transactional
    public CobrancaResponse confirmarPagamento(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Cobranca cobranca = buscarCobranca(id, escolaId);
        exigirEmAberto(cobranca, "paga");

        mudarStatus(cobranca, StatusCobranca.PAGA);
        cobranca.setPagaEm(clock.instant());
        cobranca.setAtualizadoEm(clock.instant());
        CobrancaResponse resposta = salvarEAuditar(cobranca, OperacaoAuditoria.PAGAR, escolaId);
        avisoCobrancaService.publicar(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO, CobrancaParaAviso.de(cobranca));
        return resposta;
    }

    /**
     * Lembrete de vencimento proximo, de todas as escolas: pendentes que
     * vencem de hoje ate {@value #DIAS_LEMBRETE_VENCIMENTO} dias e ainda nao
     * foram avisadas. Chamado pela rotina diaria, sem usuario autenticado.
     * Marca antes de enviar (o envio e depois do commit), entao rodar de novo
     * nao repete e-mail; um dia perdido e recuperado na execucao seguinte.
     *
     * @return quantos lembretes foram publicados
     */
    @Transactional
    public int avisarVencimentosProximos() {
        LocalDate hoje = hoje();
        List<CobrancaParaAviso> cobrancas = cobrancaRepository
                .buscarParaLembreteDeVencimento(hoje, hoje.plusDays(DIAS_LEMBRETE_VENCIMENTO)).stream()
                .map(CobrancaParaAviso::deLinha)
                .toList();
        if (cobrancas.isEmpty()) {
            return 0;
        }
        cobrancaRepository.marcarLembreteDeVencimentoEnviado(cobrancas.stream().map(CobrancaParaAviso::id).toList(),
                clock.instant());
        return avisoCobrancaService.publicar(AvisoCobranca.Tipo.VENCIMENTO_PROXIMO, cobrancas);
    }

    /**
     * Marca como EM_ATRASO as cobrancas pendentes que venceram, de todas as
     * escolas. Chamado pela rotina diaria, sem usuario autenticado; rodar de
     * novo no mesmo dia nao muda nada.
     *
     * @return quantas cobrancas entraram em atraso
     */
    @Transactional
    public int marcarVencidasComoEmAtraso() {
        return cobrancaRepository.marcarVencidasComoEmAtraso(hoje(), clock.instant());
    }

    /** Grava a cobranca e o log da operacao na mesma transacao. */
    private CobrancaResponse salvarEAuditar(Cobranca cobranca, OperacaoAuditoria operacao, UUID escolaId) {
        Cobranca salva = cobrancaRepository.saveAndFlush(cobranca);
        auditoriaService.registrar(operacao, EntidadeAuditada.COBRANCA, salva.getId());
        return paraResposta(salva, escolaId);
    }

    /** Todas as cobrancas do aluno, inclusive canceladas, da mais recente para a mais antiga. */
    private HistoricoFinanceiroResponse historico(Aluno aluno, UUID escolaId) {
        List<Cobranca> cobrancas = cobrancaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId()).stream()
                .sorted(Comparator.comparing(Cobranca::getDataVencimento).reversed())
                .toList();
        List<CobrancaResponse> respostas = paraRespostas(cobrancas, escolaId, hoje());
        String alunoNome = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .map(Pessoa::getNome)
                .orElse(null);
        return new HistoricoFinanceiroResponse(aluno.getId(), alunoNome, aluno.getMatricula(),
                HistoricoFinanceiroResponse.Resumo.de(respostas), respostas);
    }

    /** Junta os grupos (situacao gravada, vencimento) do banco por situacao efetiva. */
    private List<PaginaCobrancasResponse.TotalPorStatus> totaisPorStatus(Specification<Cobranca> filtro,
            LocalDate hoje) {
        Map<StatusCobranca, Long> quantidades = new EnumMap<>(StatusCobranca.class);
        Map<StatusCobranca, BigDecimal> valores = new EnumMap<>(StatusCobranca.class);
        for (CobrancaTotaisRepository.Grupo grupo : cobrancaRepository.agruparPorStatusEVencimento(filtro)) {
            StatusCobranca status = StatusCobranca.efetivo(grupo.status(), grupo.dataVencimento(), hoje);
            quantidades.merge(status, grupo.quantidade(), Long::sum);
            valores.merge(status, grupo.valor(), BigDecimal::add);
        }
        return Arrays.stream(StatusCobranca.values())
                .map(status -> new PaginaCobrancasResponse.TotalPorStatus(status,
                        quantidades.getOrDefault(status, 0L), valores.getOrDefault(status, BigDecimal.ZERO)))
                .toList();
    }

    /**
     * Cobranca que ja nasce dentro da janela do lembrete nao recebe o lembrete:
     * o e-mail de cobranca criada ja traz o vencimento.
     */
    private Instant dispensaLembrete(LocalDate vencimento) {
        return vencimento.isAfter(hoje().plusDays(DIAS_LEMBRETE_VENCIMENTO)) ? null : clock.instant();
    }

    private Cobranca buscarCobranca(UUID id, UUID escolaId) {
        return cobrancaRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cobranca nao encontrada"));
    }

    /** Unico ponto que troca a situacao; manter a mesma nao e mudanca. */
    private static void mudarStatus(Cobranca cobranca, StatusCobranca novo) {
        StatusCobranca atual = cobranca.getStatus();
        if (atual == novo) {
            return;
        }
        if (!atual.podeMudarPara(novo)) {
            throw new IllegalArgumentException("Cobranca %s nao pode passar para %s".formatted(atual, novo));
        }
        cobranca.setStatus(novo);
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
