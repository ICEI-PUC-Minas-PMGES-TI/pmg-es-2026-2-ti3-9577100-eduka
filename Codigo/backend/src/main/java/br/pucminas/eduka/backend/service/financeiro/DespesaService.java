package br.pucminas.eduka.backend.service.financeiro;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.Despesa;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;
import br.pucminas.eduka.backend.dto.comum.Ordenacao;
import br.pucminas.eduka.backend.dto.comum.PaginaComResumoResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.dto.financeiro.AtualizarDespesaRequest;
import br.pucminas.eduka.backend.dto.financeiro.DespesaRequest;
import br.pucminas.eduka.backend.dto.financeiro.DespesaResponse;
import br.pucminas.eduka.backend.dto.financeiro.MudarSituacaoDespesaRequest;
import br.pucminas.eduka.backend.dto.financeiro.ResumoDespesasResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.financeiro.DespesaRepository;
import br.pucminas.eduka.backend.repository.financeiro.DespesaTotaisRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Despesas da escola (RF18), com isolamento por escola (RF05, RNF03): despesa
 * e categoria sao sempre buscadas com a escola do token, e as de outra escola
 * respondem 404. Despesa e registro interno: nao passa pelo Asaas.
 *
 * <p>
 * Regras:
 * </p>
 * <ul>
 * <li>A despesa nasce A_PAGAR, ou PAGA se lancada como ja paga.</li>
 * <li>A categoria precisa ser da escola.</li>
 * <li>So despesa CANCELADA deixa de ser editada. A PAGA e editada (UC14: a
 * tela avisa do impacto antes de confirmar).</li>
 * <li>Toda troca de situacao passa por {@link SituacaoDespesa#podeMudarPara}.
 * Cancelar nao apaga: a despesa fica no historico como CANCELADA.</li>
 * <li>Criar, editar e mudar a situacao ficam no LOG_AUDITORIA com o usuario do
 * token ({@link AuditoriaService}): pagar e PAGAR, desfazer o pagamento e
 * ESTORNAR, cancelar e CANCELAR.</li>
 * </ul>
 */
@Service
public class DespesaService {

    /** Pelo vencimento; o id desempata, para a ordem nao mudar entre consultas. */
    private static final Sort ORDEM_DA_LISTAGEM = Sort.by("dataVencimento", "id");

    private final DespesaRepository despesaRepository;
    private final CategoriaDespesaService categoriaService;
    private final IsolamentoEscola isolamentoEscola;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public DespesaService(DespesaRepository despesaRepository, CategoriaDespesaService categoriaService,
            IsolamentoEscola isolamentoEscola, AuditoriaService auditoriaService, Clock clock) {
        this.despesaRepository = despesaRepository;
        this.categoriaService = categoriaService;
        this.isolamentoEscola = isolamentoEscola;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    /**
     * Despesas da escola, opcionalmente filtradas por situacao, categoria e
     * intervalo de vencimento (inclusivo). Ordenado pelo vencimento.
     *
     * @throws IllegalArgumentException se o intervalo de vencimento esta invertido
     */
    @Transactional(readOnly = true)
    public List<DespesaResponse> listar(SituacaoDespesa situacao, UUID categoriaId, LocalDate vencimentoDe,
            LocalDate vencimentoAte) {
        Set<SituacaoDespesa> situacoes = situacao == null ? Set.of() : Set.of(situacao);
        return despesaRepository.findAll(filtro(situacoes, categoriaId, vencimentoDe, vencimentoAte),
                ORDEM_DA_LISTAGEM).stream()
                .map(DespesaResponse::de)
                .toList();
    }

    /**
     * Pagina das despesas da escola, com os filtros de {@link #listar}; a
     * situacao aceita mais de uma (ex.: A_PAGAR e PAGA, para deixar as
     * canceladas de fora). O resumo soma cada situacao com os mesmos filtros,
     * menos o de situacao.
     *
     * @param situacoes vazio ou nulo lista todas
     * @throws IllegalArgumentException se o intervalo de vencimento esta invertido
     */
    @Transactional(readOnly = true)
    public PaginaComResumoResponse<DespesaResponse, ResumoDespesasResponse> listarPaginado(
            Collection<SituacaoDespesa> situacoes, UUID categoriaId, LocalDate vencimentoDe, LocalDate vencimentoAte,
            Paginacao paginacao, Ordenacao.Despesas ordenarPor, Direction direcao) {
        Specification<Despesa> daPagina = filtro(situacoes, categoriaId, vencimentoDe, vencimentoAte);
        Sort ordem = Ordenacao.noBanco(Ordenacao.ou(direcao, Direction.ASC), ordenarPor.propriedade);
        PaginaResponse<DespesaResponse> pagina = PaginaResponse.de(
                despesaRepository.findAll(daPagina, paginacao.comOrdem(ordem)), DespesaResponse::de);
        return PaginaComResumoResponse.de(pagina,
                resumo(filtro(Set.of(), categoriaId, vencimentoDe, vencimentoAte)));
    }

    /** Todas as situacoes, na ordem do enum, com zero quando nao ha despesa nela. */
    private ResumoDespesasResponse resumo(Specification<Despesa> filtro) {
        Map<SituacaoDespesa, DespesaTotaisRepository.Grupo> grupos = new EnumMap<>(SituacaoDespesa.class);
        despesaRepository.agruparPorSituacao(filtro).forEach(grupo -> grupos.put(grupo.situacao(), grupo));
        return new ResumoDespesasResponse(Arrays.stream(SituacaoDespesa.values())
                .map(situacao -> {
                    DespesaTotaisRepository.Grupo grupo = grupos.get(situacao);
                    return grupo == null
                            ? new ResumoDespesasResponse.TotalPorSituacao(situacao, 0, BigDecimal.ZERO)
                            : new ResumoDespesasResponse.TotalPorSituacao(situacao, grupo.quantidade(),
                                    grupo.valor());
                })
                .toList());
    }

    private Specification<Despesa> filtro(Collection<SituacaoDespesa> situacoes, UUID categoriaId,
            LocalDate vencimentoDe, LocalDate vencimentoAte) {
        if (vencimentoDe != null && vencimentoAte != null && vencimentoDe.isAfter(vencimentoAte)) {
            throw new IllegalArgumentException("vencimentoDe nao pode ser depois de vencimentoAte");
        }
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        // Filtros no banco: o indice (ESCOLA_ID, DATA_VENCIMENTO) evita ler o historico inteiro da escola.
        return (despesa, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            condicoes.add(cb.equal(despesa.get("escolaId"), escolaId));
            if (situacoes != null && !situacoes.isEmpty()) {
                condicoes.add(despesa.get("situacao").in(situacoes));
            }
            if (categoriaId != null) {
                condicoes.add(cb.equal(despesa.get("categoria").get("id"), categoriaId));
            }
            Path<LocalDate> vencimento = despesa.get("dataVencimento");
            if (vencimentoDe != null) {
                condicoes.add(cb.greaterThanOrEqualTo(vencimento, vencimentoDe));
            }
            if (vencimentoAte != null) {
                condicoes.add(cb.lessThanOrEqualTo(vencimento, vencimentoAte));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public DespesaResponse buscar(UUID id) {
        return DespesaResponse.de(buscarDespesa(id, isolamentoEscola.escolaIdAtual()));
    }

    /** Lanca a despesa como A_PAGAR, ou ja como PAGA se {@code request.paga()}. */
    @Transactional
    public DespesaResponse criar(DespesaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        boolean paga = Boolean.TRUE.equals(request.paga());
        Despesa despesa = Despesa.builder()
                .escolaId(escolaId)
                .categoria(categoriaService.buscarDaEscola(request.categoriaId(), escolaId))
                .descricao(request.descricao().trim())
                .responsavel(request.responsavel().trim())
                .valor(request.valor())
                .dataVencimento(request.dataVencimento())
                .situacao(paga ? SituacaoDespesa.PAGA : SituacaoDespesa.A_PAGAR)
                .pagaEm(paga ? clock.instant() : null)
                .build();
        return salvarEAuditar(despesa, OperacaoAuditoria.CRIAR);
    }

    /**
     * Substitui descricao, responsavel, categoria, valor e vencimento. A
     * situacao nao muda.
     *
     * @throws IllegalArgumentException se a despesa foi cancelada
     */
    @Transactional
    public DespesaResponse atualizar(UUID id, AtualizarDespesaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Despesa despesa = buscarDespesa(id, escolaId);
        if (despesa.getSituacao() == SituacaoDespesa.CANCELADA) {
            throw new IllegalArgumentException("Despesa CANCELADA nao pode ser editada");
        }
        despesa.setCategoria(categoriaService.buscarDaEscola(request.categoriaId(), escolaId));
        despesa.setDescricao(request.descricao().trim());
        despesa.setResponsavel(request.responsavel().trim());
        despesa.setValor(request.valor());
        despesa.setDataVencimento(request.dataVencimento());
        despesa.setAtualizadoEm(clock.instant());
        return salvarEAuditar(despesa, OperacaoAuditoria.EDITAR);
    }

    /**
     * Marca como paga, desfaz o pagamento (volta para A_PAGAR) ou cancela.
     *
     * @throws IllegalArgumentException se a despesa ja esta na situacao pedida
     *                                  ou nao pode passar para ela
     */
    @Transactional
    public DespesaResponse mudarSituacao(UUID id, MudarSituacaoDespesaRequest request) {
        Despesa despesa = buscarDespesa(id, isolamentoEscola.escolaIdAtual());
        SituacaoDespesa atual = despesa.getSituacao();
        SituacaoDespesa nova = request.situacao();
        if (atual == nova) {
            throw new IllegalArgumentException("Despesa ja esta " + atual);
        }
        if (!atual.podeMudarPara(nova)) {
            throw new IllegalArgumentException("Despesa " + atual + " nao pode passar para " + nova);
        }

        Instant agora = clock.instant();
        despesa.setSituacao(nova);
        OperacaoAuditoria operacao = switch (nova) {
            case PAGA -> {
                despesa.setPagaEm(agora);
                yield OperacaoAuditoria.PAGAR;
            }
            case A_PAGAR -> {
                despesa.setPagaEm(null);
                yield OperacaoAuditoria.ESTORNAR;
            }
            case CANCELADA -> {
                despesa.setCanceladaEm(agora);
                yield OperacaoAuditoria.CANCELAR;
            }
        };
        despesa.setAtualizadoEm(agora);
        return salvarEAuditar(despesa, operacao);
    }

    /** Grava a despesa e o log da operacao na mesma transacao. */
    private DespesaResponse salvarEAuditar(Despesa despesa, OperacaoAuditoria operacao) {
        Despesa salva = despesaRepository.saveAndFlush(despesa);
        auditoriaService.registrar(operacao, EntidadeAuditada.DESPESA, salva.getId());
        return DespesaResponse.de(salva);
    }

    private Despesa buscarDespesa(UUID id, UUID escolaId) {
        return despesaRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Despesa nao encontrada"));
    }
}
