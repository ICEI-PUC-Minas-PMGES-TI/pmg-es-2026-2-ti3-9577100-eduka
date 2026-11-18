package br.pucminas.eduka.backend.service.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.LogAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.OrigemPagamento;
import br.pucminas.eduka.backend.domain.financeiro.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse.AcaoRecente;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse.Atraso;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse.CobrancaDaSemana;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse.DespesasAPagar;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse.Mes;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse.PagamentoRecente;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse.Total;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.auditoria.LogAuditoriaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaFiltro;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaTotaisRepository;
import br.pucminas.eduka.backend.repository.financeiro.DespesaRepository;
import br.pucminas.eduka.backend.repository.financeiro.EstornoCobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.Movimento;
import br.pucminas.eduka.backend.repository.financeiro.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Painel financeiro da escola do token (RF20): o Inicio do FINANCEIRO (IU22)
 * e a parte financeira do Inicio do MASTER (IU05).
 *
 * <p>
 * As contas sao feitas no banco, para o painel nao ler o historico inteiro da
 * escola a cada abertura: totais agrupados, so o dinheiro dos meses do
 * grafico e so as {@link #LIMITE_LISTA} linhas de cada lista. A situacao de
 * cada cobranca e a efetiva ({@link StatusCobranca#efetivo}): a pendente
 * vencida ja conta como em atraso, mesmo antes da rotina diaria gravar.
 * </p>
 *
 * <p>
 * Recebido e a cobranca PAGA, pela data em que foi dada como paga
 * ({@code PAGA_EM}): pelo aviso do Asaas ou pela baixa manual. O valor e o
 * pago no provedor quando ha {@code PAGAMENTO_COBRANCA}; na baixa manual, o
 * informado nela. O estorno sai do recebido no mes em que aconteceu.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class DashboardFinanceiroService {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    /** Itens de cada lista do painel. */
    static final int LIMITE_LISTA = 10;

    /**
     * Meses do grafico "Dinheiro que entrou e saiu", contando o atual. A tela
     * filtra 3, 6 ou 12 deles sem pedir de novo.
     */
    static final int MESES_NO_GRAFICO = 12;

    /** "Vencem esta semana": de hoje ate hoje + 6 dias. */
    private static final int DIAS_DA_SEMANA = 6;

    private static final CobrancaFiltro TODAS = new CobrancaFiltro(null, null, null, null, null);
    private static final CobrancaFiltro PAGAS = new CobrancaFiltro(null, null, StatusCobranca.PAGA, null, null);
    private static final CobrancaFiltro ATRASADAS = new CobrancaFiltro(null, null, StatusCobranca.EM_ATRASO, null,
            null);
    private static final Specification<Cobranca> COM_DATA_DE_PAGAMENTO = (cobranca, query, cb) -> cb
            .isNotNull(cobranca.get("pagaEm"));
    private static final Total NADA = new Total(BigDecimal.ZERO, 0);

    /**
     * "Ultimas acoes" e o painel do financeiro: o log tem tambem alunos, notas
     * e equipe, que ficam no registro de atividades.
     */
    static final Set<EntidadeAuditada> ENTIDADES_FINANCEIRAS = EnumSet.of(EntidadeAuditada.COBRANCA,
            EntidadeAuditada.PAGAMENTO, EntidadeAuditada.DESPESA, EntidadeAuditada.CONTA_RECEBIMENTO,
            EntidadeAuditada.FATURA_PLATAFORMA);

    private final CobrancaRepository cobrancaRepository;
    private final PagamentoCobrancaRepository pagamentoRepository;
    private final DespesaRepository despesaRepository;
    private final EstornoCobrancaRepository estornoRepository;
    private final LogAuditoriaRepository logAuditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public DashboardFinanceiroService(CobrancaRepository cobrancaRepository,
            PagamentoCobrancaRepository pagamentoRepository, DespesaRepository despesaRepository,
            EstornoCobrancaRepository estornoRepository, LogAuditoriaRepository logAuditoriaRepository, UsuarioRepository usuarioRepository,
            PessoaRepository pessoaRepository, IsolamentoEscola isolamentoEscola, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.despesaRepository = despesaRepository;
        this.estornoRepository = estornoRepository;
        this.logAuditoriaRepository = logAuditoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    public DashboardFinanceiroResponse painel() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        LocalDate hoje = LocalDate.now(clock.withZone(FUSO_ESCOLA));
        YearMonth mesAtual = YearMonth.from(hoje);

        // So o dinheiro dos meses do grafico: o mes atual sai dele tambem.
        YearMonth primeiroDoGrafico = mesAtual.minusMonths(MESES_NO_GRAFICO - 1L);
        Instant inicioDoGrafico = inicioDe(primeiroDoGrafico.atDay(1));
        Instant fimDoGrafico = inicioDe(mesAtual.plusMonths(1).atDay(1));
        List<Movimento> recebidos = cobrancaRepository.recebidosEntre(escolaId, inicioDoGrafico, fimDoGrafico);
        List<Movimento> entradas = comEstornos(recebidos, escolaId, inicioDoGrafico, fimDoGrafico);
        long pagamentosNoMes = recebidos.stream().filter(r -> mesDe(r.getPagaEm()).equals(mesAtual)).count();
        List<Movimento> entradasNoMes = entradas.stream()
                .filter(r -> mesDe(r.getPagaEm()).equals(mesAtual))
                .toList();

        // Em aberto, em atraso e pago ja vencido saem dos totais agrupados, sem ler cada cobranca.
        Map<StatusCobranca, Total> porStatus = new EnumMap<>(StatusCobranca.class);
        BigDecimal pagoJaVencido = BigDecimal.ZERO;
        for (CobrancaTotaisRepository.Grupo grupo : cobrancaRepository
                .agruparPorStatusEVencimento(TODAS.daEscolaEmQualquerStatus(escolaId))) {
            StatusCobranca status = StatusCobranca.efetivo(grupo.status(), grupo.dataVencimento(), hoje);
            porStatus.merge(status, new Total(grupo.valor(), grupo.quantidade()),
                    (a, b) -> new Total(a.valor().add(b.valor()), a.quantidade() + b.quantidade()));
            if (status == StatusCobranca.PAGA && grupo.dataVencimento().isBefore(hoje)) {
                pagoJaVencido = pagoJaVencido.add(grupo.valor());
            }
        }
        Total pendentes = porStatus.getOrDefault(StatusCobranca.PENDENTE, NADA);
        Total emAtraso = porStatus.getOrDefault(StatusCobranca.EM_ATRASO, NADA);

        // As em atraso e as que vencem na semana, ate LIMITE_LISTA de cada, o vencimento mais antigo primeiro.
        // Separadas: numa lista so, atrasos antigos ocupariam todas as vagas e a semana sumiria da tela.
        PageRequest porVencimento = PageRequest.of(0, LIMITE_LISTA, Sort.by("dataVencimento", "id"));
        List<Cobranca> daSemana = new ArrayList<>(
                cobrancaRepository.findAll(ATRASADAS.daEscola(escolaId, hoje), porVencimento).getContent());
        daSemana.addAll(cobrancaRepository.findAll(new CobrancaFiltro(null, null, StatusCobranca.PENDENTE, hoje,
                hoje.plusDays(DIAS_DA_SEMANA)).daEscola(escolaId, hoje), porVencimento).getContent());
        List<Recebido> ultimosRecebidos = recebidos(cobrancaRepository.findAll(
                PAGAS.daEscola(escolaId, hoje).and(COM_DATA_DE_PAGAMENTO),
                PageRequest.of(0, LIMITE_LISTA, Sort.by(Sort.Direction.DESC, "pagaEm", "id"))).getContent(),
                escolaId);
        List<LogAuditoria> acoes = logAuditoriaRepository.findTop10ByEscolaIdAndEntidadeInOrderByDataHoraDesc(escolaId,
                ENTIDADES_FINANCEIRAS);
        Map<UUID, Usuario> autores = usuarioRepository
                .findAllById(acoes.stream().map(LogAuditoria::getUsuarioId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));

        // Nomes cifrados de alunos e usuarios das listas, numa consulta so.
        Set<UUID> pessoaIds = new HashSet<>();
        daSemana.forEach(c -> pessoaIds.add(c.getAluno().getPessoaId()));
        ultimosRecebidos.forEach(r -> pessoaIds.add(r.cobranca().getAluno().getPessoaId()));
        autores.values().forEach(u -> pessoaIds.add(u.getPessoaId()));
        Map<UUID, String> nomes = pessoaIds.isEmpty() ? Map.of()
                : pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                        .collect(Collectors.toMap(Pessoa::getId, Pessoa::getNome));
        Function<Cobranca, String> nomeDoAluno = c -> nomes.get(c.getAluno().getPessoaId());

        long alunosEmAtraso = emAtraso.quantidade() == 0 ? 0
                : cobrancaRepository.contarAlunos(ATRASADAS.daEscola(escolaId, hoje));

        return new DashboardFinanceiroResponse(
                new Total(somar(entradasNoMes.stream().map(Movimento::getValor)), pagamentosNoMes),
                new Total(pendentes.valor().add(emAtraso.valor()), pendentes.quantidade() + emAtraso.quantidade()),
                new Atraso(alunosEmAtraso, emAtraso.quantidade(), emAtraso.valor(),
                        percentual(emAtraso.valor(), emAtraso.valor().add(pagoJaVencido))),
                despesasAPagar(escolaId, hoje),
                porMes(entradas, despesaRepository.pagasEntre(escolaId, inicioDoGrafico, fimDoGrafico),
                        primeiroDoGrafico, mesAtual),
                daSemana.stream()
                        .map(c -> new CobrancaDaSemana(c.getId(), c.getAluno().getId(), nomeDoAluno.apply(c),
                                c.getDescricao(), c.getValor(), c.getDataVencimento(),
                                StatusCobranca.efetivo(c.getStatus(), c.getDataVencimento(), hoje)))
                        .toList(),
                ultimosRecebidos.stream()
                        .map(r -> new PagamentoRecente(r.cobranca().getId(), nomeDoAluno.apply(r.cobranca()),
                                r.cobranca().getDescricao(), r.valor(), r.forma(),
                                r.cobranca().getFormaPagamentoManual(), r.origem(), r.pagoEm()))
                        .toList(),
                acoes.stream()
                        .map(a -> {
                            Usuario autor = autores.get(a.getUsuarioId());
                            return new AcaoRecente(a.getUsuarioId(),
                                    autor != null ? nomes.get(autor.getPessoaId()) : null,
                                    autor != null ? autor.getPerfil() : null,
                                    a.getOperacao(), a.getEntidade(), a.getRegistroId(), a.getDataHora());
                        })
                        .toList());
    }

    /** Cobranca paga, com o que entrou: o pagamento do provedor, se houver, ou a baixa manual. */
    private record Recebido(Cobranca cobranca, BigDecimal valor, FormaPagamento forma, OrigemPagamento origem,
            Instant pagoEm) {
    }

    /** Na ordem de {@code cobrancas}; as que nao estao pagas ficam de fora. */
    private List<Recebido> recebidos(List<Cobranca> cobrancas, UUID escolaId) {
        List<Cobranca> pagas = cobrancas.stream()
                .filter(c -> c.getStatus() == StatusCobranca.PAGA && c.getPagaEm() != null)
                .toList();
        if (pagas.isEmpty()) {
            return List.of();
        }
        Map<UUID, PagamentoCobranca> pagamentos = pagamentoRepository
                .findAllByEscolaIdAndCobrancaIdIn(escolaId, pagas.stream().map(Cobranca::getId).toList()).stream()
                .collect(Collectors.toMap(PagamentoCobranca::getCobrancaId, Function.identity()));
        return pagas.stream().map(c -> {
            PagamentoCobranca pagamento = pagamentos.get(c.getId());
            return pagamento == null
                    ? new Recebido(c, CobrancaService.valorRecebidoNaBaixa(c), null, OrigemPagamento.MANUAL,
                            c.getPagaEm())
                    : new Recebido(c, pagamento.getValorPago(), pagamento.getFormaPagamento(),
                            pagamento.getOrigem(), c.getPagaEm());
        }).toList();
    }

    /** A pagar ate o fim do mes, e o proximo vencimento de hoje em diante (pode ser de outro mes). */
    private DespesasAPagar despesasAPagar(UUID escolaId, LocalDate hoje) {
        DespesaRepository.Soma ateOFimDoMes = despesaRepository.somarAPagarAte(escolaId,
                YearMonth.from(hoje).atEndOfMonth());
        LocalDate proximo = despesaRepository.proximoVencimentoAPagar(escolaId, hoje).orElse(null);
        BigDecimal valor = ateOFimDoMes.getValor() != null ? ateOFimDoMes.getValor() : BigDecimal.ZERO;
        return new DespesasAPagar(valor, ateOFimDoMes.getQuantidade(), proximo);
    }

    /**
     * Entrou e saiu de cada mes de {@code de} a {@code ate} (inclusive), pelo
     * dia em que foi pago, no fuso da escola: o filtro com calendario do
     * grafico. Os meses das pontas contam so os dias do periodo.
     *
     * @throws IllegalArgumentException se o periodo for invalido (ver {@link PeriodoFinanceiro})
     */
    public List<Mes> movimento(LocalDate de, LocalDate ate) {
        PeriodoFinanceiro periodo = new PeriodoFinanceiro(de, ate);
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return porMes(comEstornos(cobrancaRepository.recebidosEntre(escolaId, periodo.inicio(), periodo.fim()), escolaId,
                periodo.inicio(), periodo.fim()),
                despesaRepository.pagasEntre(escolaId, periodo.inicio(), periodo.fim()), periodo.primeiroMes(),
                periodo.ultimoMes());
    }

    /** Os recebidos e, com valor negativo, os estornos do mesmo intervalo. */
    private List<Movimento> comEstornos(List<Movimento> recebidos, UUID escolaId, Instant desde, Instant antes) {
        return Stream.concat(recebidos.stream(), estornoRepository.estornosEntre(escolaId, desde, antes).stream())
                .toList();
    }

    /** Um {@link Mes} para cada mes de {@code primeiro} a {@code ultimo}, mesmo os sem movimento. */
    private static List<Mes> porMes(List<Movimento> recebidos, List<Movimento> despesasPagas, YearMonth primeiro,
            YearMonth ultimo) {
        Map<YearMonth, BigDecimal> entrou = somarPorMes(recebidos);
        Map<YearMonth, BigDecimal> saiu = somarPorMes(despesasPagas);
        return Stream.iterate(primeiro, m -> !m.isAfter(ultimo), m -> m.plusMonths(1))
                .map(m -> new Mes(m, entrou.getOrDefault(m, BigDecimal.ZERO), saiu.getOrDefault(m, BigDecimal.ZERO)))
                .toList();
    }

    /** Meia-noite do dia no fuso da escola. */
    private static Instant inicioDe(LocalDate dia) {
        return dia.atStartOfDay(FUSO_ESCOLA).toInstant();
    }

    /** Pelo mes de {@code PAGA_EM} no fuso da escola. */
    private static Map<YearMonth, BigDecimal> somarPorMes(List<Movimento> movimentos) {
        return movimentos.stream().collect(Collectors.groupingBy(m -> mesDe(m.getPagaEm()),
                Collectors.reducing(BigDecimal.ZERO, Movimento::getValor, BigDecimal::add)));
    }

    private static YearMonth mesDe(Instant instante) {
        return YearMonth.from(instante.atZone(FUSO_ESCOLA));
    }

    private static BigDecimal somar(Stream<BigDecimal> valores) {
        return valores.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 0 a 100, arredondado; nulo quando nao ha base. */
    private static Integer percentual(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? null
                : parte.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }
}
