package br.pucminas.eduka.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.Despesa;
import br.pucminas.eduka.backend.domain.LogAuditoria;
import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.AcaoRecente;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.Atraso;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.CobrancaDaSemana;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.DespesasAPagar;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.Mes;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.PagamentoRecente;
import br.pucminas.eduka.backend.dto.DashboardFinanceiroResponse.Total;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.DespesaRepository;
import br.pucminas.eduka.backend.repository.LogAuditoriaRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Painel financeiro da escola do token (RF20): o Inicio do FINANCEIRO (IU22)
 * e a parte financeira do Inicio do MASTER (IU05).
 *
 * <p>
 * Como em {@link DashboardService}, as contas sao feitas em memoria sobre as
 * cobrancas e despesas da escola. A situacao de cada cobranca e a efetiva
 * ({@link StatusCobranca#efetivo}): a pendente vencida ja conta como em
 * atraso, mesmo antes da rotina diaria gravar.
 * </p>
 *
 * <p>
 * Recebido e a cobranca PAGA, pela data em que foi dada como paga
 * ({@code PAGA_EM}): pelo aviso do Asaas ou pela baixa manual. O valor e o
 * pago no provedor quando ha {@code PAGAMENTO_COBRANCA}; na baixa manual, o
 * da cobranca.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class DashboardFinanceiroService {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    /** Itens de cada lista do painel. */
    static final int LIMITE_LISTA = 10;

    /** Meses do grafico "Dinheiro que entrou e saiu", contando o atual. */
    static final int MESES_NO_GRAFICO = 6;

    /** "Vencem esta semana": de hoje ate hoje + 6 dias. */
    private static final int DIAS_DA_SEMANA = 6;

    private final CobrancaRepository cobrancaRepository;
    private final PagamentoCobrancaRepository pagamentoRepository;
    private final DespesaRepository despesaRepository;
    private final LogAuditoriaRepository logAuditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public DashboardFinanceiroService(CobrancaRepository cobrancaRepository,
            PagamentoCobrancaRepository pagamentoRepository, DespesaRepository despesaRepository,
            LogAuditoriaRepository logAuditoriaRepository, UsuarioRepository usuarioRepository,
            PessoaRepository pessoaRepository, IsolamentoEscola isolamentoEscola, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.despesaRepository = despesaRepository;
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

        List<Cobranca> cobrancas = cobrancaRepository.findAllByEscolaId(escolaId);
        Map<UUID, StatusCobranca> status = cobrancas.stream().collect(Collectors.toMap(Cobranca::getId,
                c -> StatusCobranca.efetivo(c.getStatus(), c.getDataVencimento(), hoje)));
        List<Cobranca> emAberto = cobrancas.stream().filter(c -> status.get(c.getId()).emAberto()).toList();
        List<Cobranca> emAtraso = emAberto.stream()
                .filter(c -> status.get(c.getId()) == StatusCobranca.EM_ATRASO)
                .toList();

        List<Recebido> recebidos = recebidos(cobrancas, escolaId);
        List<Recebido> recebidosNoMes = recebidos.stream().filter(r -> mesDe(r.pagoEm()).equals(mesAtual)).toList();

        List<Despesa> despesas = despesaRepository.findAllByEscolaId(escolaId);

        List<Cobranca> daSemana = emAberto.stream()
                .filter(c -> !c.getDataVencimento().isAfter(hoje.plusDays(DIAS_DA_SEMANA)))
                .sorted(Comparator.comparing(Cobranca::getDataVencimento))
                .limit(LIMITE_LISTA)
                .toList();
        List<Recebido> ultimosRecebidos = recebidos.stream()
                .sorted(Comparator.comparing(Recebido::pagoEm).reversed())
                .limit(LIMITE_LISTA)
                .toList();
        List<LogAuditoria> acoes = logAuditoriaRepository.findTop10ByEscolaIdOrderByDataHoraDesc(escolaId);
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

        return new DashboardFinanceiroResponse(
                new Total(somar(recebidosNoMes.stream().map(Recebido::valor)), recebidosNoMes.size()),
                new Total(somar(emAberto.stream().map(Cobranca::getValor)), emAberto.size()),
                atraso(cobrancas, emAtraso, status, hoje),
                despesasAPagar(despesas, hoje),
                ultimosMeses(recebidos, despesas, mesAtual),
                daSemana.stream()
                        .map(c -> new CobrancaDaSemana(c.getId(), c.getAluno().getId(), nomeDoAluno.apply(c),
                                c.getDescricao(), c.getValor(), c.getDataVencimento(), status.get(c.getId())))
                        .toList(),
                ultimosRecebidos.stream()
                        .map(r -> new PagamentoRecente(r.cobranca().getId(), nomeDoAluno.apply(r.cobranca()),
                                r.cobranca().getDescricao(), r.valor(), r.forma(), r.origem(), r.pagoEm()))
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
                    ? new Recebido(c, c.getValor(), null, OrigemPagamento.MANUAL, c.getPagaEm())
                    : new Recebido(c, pagamento.getValorPago(), pagamento.getFormaPagamento(),
                            pagamento.getOrigem(), c.getPagaEm());
        }).toList();
    }

    private static Atraso atraso(List<Cobranca> cobrancas, List<Cobranca> emAtraso, Map<UUID, StatusCobranca> status,
            LocalDate hoje) {
        BigDecimal valorEmAtraso = somar(emAtraso.stream().map(Cobranca::getValor));
        BigDecimal pagoJaVencido = somar(cobrancas.stream()
                .filter(c -> status.get(c.getId()) == StatusCobranca.PAGA && c.getDataVencimento().isBefore(hoje))
                .map(Cobranca::getValor));
        long alunos = emAtraso.stream().map(c -> c.getAluno().getId()).distinct().count();
        return new Atraso(alunos, emAtraso.size(), valorEmAtraso, percentual(valorEmAtraso,
                valorEmAtraso.add(pagoJaVencido)));
    }

    private static DespesasAPagar despesasAPagar(List<Despesa> despesas, LocalDate hoje) {
        LocalDate fimDoMes = YearMonth.from(hoje).atEndOfMonth();
        List<Despesa> aPagar = despesas.stream().filter(d -> d.getSituacao() == SituacaoDespesa.A_PAGAR).toList();
        List<Despesa> ateOFimDoMes = aPagar.stream().filter(d -> !d.getDataVencimento().isAfter(fimDoMes)).toList();
        LocalDate proximo = aPagar.stream()
                .map(Despesa::getDataVencimento)
                .filter(v -> !v.isBefore(hoje))
                .min(Comparator.naturalOrder())
                .orElse(null);
        return new DespesasAPagar(somar(ateOFimDoMes.stream().map(Despesa::getValor)), ateOFimDoMes.size(), proximo);
    }

    private static List<Mes> ultimosMeses(List<Recebido> recebidos, List<Despesa> despesas, YearMonth mesAtual) {
        Map<YearMonth, BigDecimal> entrou = recebidos.stream()
                .collect(Collectors.groupingBy(r -> mesDe(r.pagoEm()),
                        Collectors.reducing(BigDecimal.ZERO, Recebido::valor, BigDecimal::add)));
        Map<YearMonth, BigDecimal> saiu = despesas.stream()
                .filter(d -> d.getSituacao() == SituacaoDespesa.PAGA && d.getPagaEm() != null)
                .collect(Collectors.groupingBy(d -> mesDe(d.getPagaEm()),
                        Collectors.reducing(BigDecimal.ZERO, Despesa::getValor, BigDecimal::add)));
        return Stream.iterate(mesAtual.minusMonths(MESES_NO_GRAFICO - 1L), m -> m.plusMonths(1))
                .limit(MESES_NO_GRAFICO)
                .map(m -> new Mes(m, entrou.getOrDefault(m, BigDecimal.ZERO), saiu.getOrDefault(m, BigDecimal.ZERO)))
                .toList();
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
