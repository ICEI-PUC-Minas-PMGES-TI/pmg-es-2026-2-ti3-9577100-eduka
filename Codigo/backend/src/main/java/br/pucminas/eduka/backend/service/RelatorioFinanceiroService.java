package br.pucminas.eduka.backend.service;

import static br.pucminas.eduka.backend.service.PeriodoFinanceiro.FUSO_ESCOLA;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.Despesa;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.TipoCobranca;
import br.pucminas.eduka.backend.dto.ArquivoCsv;
import br.pucminas.eduka.backend.dto.RelatorioFinanceiroResponse;
import br.pucminas.eduka.backend.dto.RelatorioFinanceiroResponse.Forma;
import br.pucminas.eduka.backend.dto.RelatorioFinanceiroResponse.Inadimplencia;
import br.pucminas.eduka.backend.dto.RelatorioFinanceiroResponse.Linha;
import br.pucminas.eduka.backend.dto.RelatorioFinanceiroResponse.Mes;
import br.pucminas.eduka.backend.dto.RelatorioFinanceiroResponse.Resumo;
import br.pucminas.eduka.backend.dto.RelatorioFinanceiroResponse.Total;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository.CobrancasDoGrupo;
import br.pucminas.eduka.backend.repository.CobrancaRepository.Recebimento;
import br.pucminas.eduka.backend.repository.DespesaRepository;
import br.pucminas.eduka.backend.repository.DespesaRepository.SomaDoVencimento;
import br.pucminas.eduka.backend.repository.EstornoCobrancaRepository;
import br.pucminas.eduka.backend.repository.Movimento;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Relatorios financeiros da escola do token (RF19): os numeros da IU19 e a
 * exportacao em CSV da IU19-A. So leem: nao participam do pagamento.
 *
 * <p>
 * Recebido e saldo seguem o painel financeiro
 * ({@link DashboardFinanceiroService}): a cobranca PAGA, pelo dia do pagamento,
 * com o valor pago no provedor ou, na baixa manual, o da cobranca; menos os
 * estornos, no periodo do estorno; menos as despesas pagas, pelo dia do
 * pagamento. Cobrancas e despesas lancadas contam pelo vencimento, sem as
 * canceladas: o CSV soma o mesmo que a tela.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class RelatorioFinanceiroService {

    private static final String SEM_CURSO = "Sem curso";

    private final CobrancaRepository cobrancaRepository;
    private final DespesaRepository despesaRepository;
    private final EstornoCobrancaRepository estornoRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public RelatorioFinanceiroService(CobrancaRepository cobrancaRepository, DespesaRepository despesaRepository,
            EstornoCobrancaRepository estornoRepository, PessoaRepository pessoaRepository,
            IsolamentoEscola isolamentoEscola, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.despesaRepository = despesaRepository;
        this.estornoRepository = estornoRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException se o periodo for invalido (ver {@link PeriodoFinanceiro}) */
    public RelatorioFinanceiroResponse gerar(LocalDate de, LocalDate ate) {
        PeriodoFinanceiro periodo = new PeriodoFinanceiro(de, ate);
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        LocalDate hoje = hoje();

        List<Recebimento> estornos = estornoRepository.estornosEntre(escolaId, periodo.inicio(), periodo.fim());
        List<Recebimento> entradas = Stream.concat(
                cobrancaRepository.recebimentosEntre(escolaId, periodo.inicio(), periodo.fim()).stream(),
                estornos.stream()).toList();
        List<SomaDoVencimento> lancadas = despesaRepository.lancadasPorVencimento(escolaId, de, ate);
        List<Movimento> despesasPagas = despesaRepository.pagasEntre(escolaId, periodo.inicio(), periodo.fim());
        List<CobrancasDoGrupo> cobrancas = cobrancaRepository.totaisPorVencimento(escolaId, de, ate);

        BigDecimal recebido = somar(entradas.stream().map(Recebimento::getValor));
        BigDecimal pagas = somar(despesasPagas.stream().map(Movimento::getValor));
        Acumulado todas = agrupar(cobrancas, g -> "todas", hoje).getOrDefault("todas", new Acumulado());

        return new RelatorioFinanceiroResponse(de, ate,
                new Total(recebido, entradas.size() - estornos.size()),
                new Total(somar(estornos.stream().map(Recebimento::getValor)).negate(), estornos.size()),
                new Total(somar(lancadas.stream().map(SomaDoVencimento::getValor)),
                        lancadas.stream().mapToLong(SomaDoVencimento::getQuantidade).sum()),
                new Total(pagas, despesasPagas.size()),
                recebido.subtract(pagas),
                new Inadimplencia(todas.emAtraso, todas.cobrancasEmAtraso, todas.inadimplencia()),
                porMes(entradas, despesasPagas, periodo),
                formas(entradas, recebido),
                new Resumo(
                        resumoPorCurso(cobrancas, hoje),
                        resumoOrdenado(cobrancas, CobrancasDoGrupo::getTipo, hoje),
                        resumoOrdenado(cobrancas, g -> YearMonth.from(g.getVencimento()), hoje),
                        todas.linha(null)));
    }

    /**
     * Um arquivo so com uma secao para cada conjunto pedido, na ordem de
     * {@link ArquivoCsv.Conjunto}. Le as linhas de uma vez: o periodo limitado
     * segura o tamanho.
     *
     * @throws IllegalArgumentException se nenhum conjunto foi pedido ou o
     *                                  periodo for invalido
     */
    public ArquivoCsv exportar(LocalDate de, LocalDate ate, Set<ArquivoCsv.Conjunto> conjuntos,
            ArquivoCsv.Separador separador) {
        if (conjuntos.isEmpty()) {
            throw new IllegalArgumentException("Escolha ao menos um conjunto de dados para exportar");
        }
        PeriodoFinanceiro periodo = new PeriodoFinanceiro(de, ate);
        UUID escolaId = isolamentoEscola.escolaIdAtual();

        List<Cobranca> cobrancas = conjuntos.contains(ArquivoCsv.Conjunto.COBRANCAS)
                ? cobrancaRepository.naoCanceladasQueVencemEntre(escolaId, de, ate)
                : List.of();
        List<Recebimento> pagamentos = conjuntos.contains(ArquivoCsv.Conjunto.PAGAMENTOS)
                ? Stream.concat(cobrancaRepository.recebimentosEntre(escolaId, periodo.inicio(), periodo.fim()).stream(),
                        estornoRepository.estornosEntre(escolaId, periodo.inicio(), periodo.fim()).stream())
                        .sorted(Comparator.comparing(Recebimento::getPagaEm))
                        .toList()
                : List.of();
        Map<UUID, String> nomes = nomesDosAlunos(cobrancas, pagamentos, escolaId);

        PlanilhaCsv planilha = new PlanilhaCsv(separador);
        if (conjuntos.contains(ArquivoCsv.Conjunto.COBRANCAS)) {
            escreverCobrancas(planilha, cobrancas, nomes);
        }
        if (conjuntos.contains(ArquivoCsv.Conjunto.PAGAMENTOS)) {
            escreverPagamentos(planilha, pagamentos, nomes);
        }
        if (conjuntos.contains(ArquivoCsv.Conjunto.DESPESAS)) {
            escreverDespesas(planilha, despesaRepository.lancadasEntre(escolaId, de, ate));
        }
        return new ArquivoCsv("relatorio-financeiro-%s-a-%s.csv".formatted(de, ate), planilha.paraBytes());
    }

    // ---- Numeros da tela ----

    /** O que as cobrancas de um grupo somam. */
    private static final class Acumulado {
        private BigDecimal cobrado = BigDecimal.ZERO;
        private BigDecimal recebido = BigDecimal.ZERO;
        private BigDecimal emAberto = BigDecimal.ZERO;
        private BigDecimal emAtraso = BigDecimal.ZERO;
        private BigDecimal pagoJaVencido = BigDecimal.ZERO;
        private long cobrancasEmAtraso;

        private void somar(CobrancasDoGrupo grupo, LocalDate hoje) {
            StatusCobranca status = StatusCobranca.efetivo(grupo.getStatus(), grupo.getVencimento(), hoje);
            cobrado = cobrado.add(grupo.getValor());
            if (status == StatusCobranca.PAGA) {
                recebido = recebido.add(grupo.getValorPago());
                if (grupo.getVencimento().isBefore(hoje)) {
                    pagoJaVencido = pagoJaVencido.add(grupo.getValor());
                }
                return;
            }
            emAberto = emAberto.add(grupo.getValor());
            if (status == StatusCobranca.EM_ATRASO) {
                emAtraso = emAtraso.add(grupo.getValor());
                cobrancasEmAtraso += grupo.getQuantidade();
            }
        }

        private BigDecimal inadimplencia() {
            return percentual(emAtraso, emAtraso.add(pagoJaVencido));
        }

        private Linha linha(String grupo) {
            return new Linha(grupo, cobrado, recebido, emAberto, inadimplencia());
        }
    }

    private static <K> Map<K, Acumulado> agrupar(List<CobrancasDoGrupo> grupos, Function<CobrancasDoGrupo, K> chave,
            LocalDate hoje) {
        Map<K, Acumulado> acumulados = new LinkedHashMap<>();
        grupos.forEach(g -> acumulados.computeIfAbsent(chave.apply(g), k -> new Acumulado()).somar(g, hoje));
        return acumulados;
    }

    /** Pelo id do curso: dois cursos podem ter o mesmo nome. */
    private static List<Linha> resumoPorCurso(List<CobrancasDoGrupo> cobrancas, LocalDate hoje) {
        record ChaveCurso(Long id, String nome) {
        }
        return agrupar(cobrancas, g -> new ChaveCurso(g.getCursoId(), g.getCursoNome()), hoje).entrySet().stream()
                .map(e -> e.getValue().linha(e.getKey().id() != null ? e.getKey().nome() : SEM_CURSO))
                .sorted(Comparator.comparing(Linha::cobrado).reversed().thenComparing(Linha::grupo))
                .toList();
    }

    /** Na ordem natural da chave; o grupo e a chave em texto. */
    private static <K extends Comparable<K>> List<Linha> resumoOrdenado(List<CobrancasDoGrupo> cobrancas,
            Function<CobrancasDoGrupo, K> chave, LocalDate hoje) {
        return agrupar(cobrancas, chave, hoje).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().linha(e.getKey().toString()))
                .toList();
    }

    /** Um {@link Mes} para cada mes do periodo, mesmo os sem movimento. */
    private static List<Mes> porMes(List<? extends Movimento> entradas, List<? extends Movimento> despesasPagas,
            PeriodoFinanceiro periodo) {
        Map<YearMonth, BigDecimal> entrou = somarPorMes(entradas);
        Map<YearMonth, BigDecimal> saiu = somarPorMes(despesasPagas);
        return Stream.iterate(periodo.primeiroMes(), m -> !m.isAfter(periodo.ultimoMes()), m -> m.plusMonths(1))
                .map(m -> {
                    BigDecimal receitas = entrou.getOrDefault(m, BigDecimal.ZERO);
                    BigDecimal despesas = saiu.getOrDefault(m, BigDecimal.ZERO);
                    return new Mes(m, receitas, despesas, receitas.subtract(despesas));
                })
                .toList();
    }

    /** Pelo mes do dia em que o dinheiro se moveu, no fuso da escola. */
    private static Map<YearMonth, BigDecimal> somarPorMes(List<? extends Movimento> movimentos) {
        return movimentos.stream().collect(Collectors.groupingBy(
                m -> YearMonth.from(m.getPagaEm().atZone(FUSO_ESCOLA)),
                Collectors.reducing(BigDecimal.ZERO, Movimento::getValor, BigDecimal::add)));
    }

    /** PIX e BOLETO sempre, para a tela ter as duas barras; a baixa manual so se houver. */
    private static List<Forma> formas(List<Recebimento> entradas, BigDecimal recebido) {
        List<Forma> formas = new ArrayList<>();
        for (FormaPagamento forma : FormaPagamento.values()) {
            formas.add(forma(entradas, forma, recebido));
        }
        Forma manual = forma(entradas, null, recebido);
        if (manual.quantidade() > 0) {
            formas.add(manual);
        }
        return formas;
    }

    private static Forma forma(List<Recebimento> entradas, FormaPagamento forma, BigDecimal recebido) {
        List<BigDecimal> valores = entradas.stream()
                .filter(r -> Objects.equals(r.getForma(), forma))
                .map(Recebimento::getValor)
                .toList();
        BigDecimal soma = somar(valores.stream());
        return new Forma(forma, soma, valores.stream().filter(v -> v.signum() > 0).count(), percentual(soma, recebido));
    }

    private static BigDecimal somar(Stream<BigDecimal> valores) {
        return valores.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Com uma casa; nulo quando nao ha base positiva, como num periodo so com estornos. */
    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        return total.signum() <= 0 ? null
                : parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    // ---- Exportacao ----

    private void escreverCobrancas(PlanilhaCsv planilha, List<Cobranca> cobrancas, Map<UUID, String> nomes) {
        LocalDate hoje = hoje();
        planilha.secao("Cobranças", "Aluno", "Curso", "Tipo", "Descrição", "Valor", "Vencimento", "Situação",
                "Pago em");
        for (Cobranca c : cobrancas) {
            planilha.linha(nomes.get(c.getAluno().getPessoaId()), nomeDoCurso(c.getMatricula()), rotulo(c.getTipo()),
                    c.getDescricao(), c.getValor(), c.getDataVencimento(),
                    rotulo(StatusCobranca.efetivo(c.getStatus(), c.getDataVencimento(), hoje)), dia(c.getPagaEm()));
        }
    }

    private static void escreverPagamentos(PlanilhaCsv planilha, List<Recebimento> pagamentos,
            Map<UUID, String> nomes) {
        planilha.secao("Pagamentos", "Data", "Aluno", "Descrição", "Valor", "Forma", "Movimento");
        for (Recebimento p : pagamentos) {
            planilha.linha(dia(p.getPagaEm()), nomes.get(p.getPessoaId()), p.getDescricao(), p.getValor(),
                    rotulo(p.getForma()), p.getValor().signum() < 0 ? "Estorno" : "Pagamento");
        }
    }

    private static void escreverDespesas(PlanilhaCsv planilha, List<Despesa> despesas) {
        planilha.secao("Despesas", "Vencimento", "Categoria", "Descrição", "Valor", "Situação", "Paga em");
        for (Despesa d : despesas) {
            planilha.linha(d.getDataVencimento(), d.getCategoria().getNome(), d.getDescricao(), d.getValor(),
                    rotulo(d.getSituacao()), dia(d.getPagaEm()));
        }
    }

    /** Nomes cifrados dos alunos das linhas, numa consulta so. */
    private Map<UUID, String> nomesDosAlunos(List<Cobranca> cobrancas, List<Recebimento> pagamentos, UUID escolaId) {
        Set<UUID> pessoaIds = Stream.concat(cobrancas.stream().map(c -> c.getAluno().getPessoaId()),
                pagamentos.stream().map(Recebimento::getPessoaId)).collect(Collectors.toSet());
        return pessoaIds.isEmpty() ? Map.of()
                : pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                        .collect(Collectors.toMap(Pessoa::getId, Pessoa::getNome));
    }

    private static String nomeDoCurso(MatriculaTurma matricula) {
        Curso curso = matricula != null ? matricula.getTurma().getCurso() : null;
        return curso != null ? curso.getNome() : SEM_CURSO;
    }

    private static LocalDate dia(Instant instante) {
        return instante != null ? LocalDate.ofInstant(instante, FUSO_ESCOLA) : null;
    }

    private static String rotulo(TipoCobranca tipo) {
        return switch (tipo) {
            case MENSALIDADE -> "Mensalidade";
            case MATRICULA -> "Matrícula";
            case MATERIAL -> "Material";
            case OUTRO -> "Outro";
        };
    }

    private static String rotulo(StatusCobranca status) {
        return switch (status) {
            case PENDENTE -> "Pendente";
            case EM_ATRASO -> "Em atraso";
            case PAGA -> "Paga";
            case CANCELADA -> "Cancelada";
        };
    }

    private static String rotulo(SituacaoDespesa situacao) {
        return switch (situacao) {
            case A_PAGAR -> "A pagar";
            case PAGA -> "Paga";
            case CANCELADA -> "Cancelada";
        };
    }

    private static String rotulo(FormaPagamento forma) {
        return switch (forma) {
            case null -> "Baixa manual";
            case PIX -> "PIX";
            case BOLETO -> "Boleto";
        };
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }
}
