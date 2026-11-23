package br.pucminas.eduka.backend.service.financeiro;

import static br.pucminas.eduka.backend.service.financeiro.PeriodoFinanceiro.FUSO_ESCOLA;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.Curso;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.comum.Endereco;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.Despesa;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.financeiro.TipoCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.comum.ArquivoCsv;
import br.pucminas.eduka.backend.dto.comum.ArquivoPlanilha;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Forma;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Inadimplencia;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Linha;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Mes;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Resumo;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse.Total;
import br.pucminas.eduka.backend.dto.financeiro.RelatorioFinanceiroResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository.CobrancasDoGrupo;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository.Recebimento;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.DespesaRepository.SomaDoVencimento;
import br.pucminas.eduka.backend.repository.financeiro.DespesaRepository;
import br.pucminas.eduka.backend.repository.financeiro.EstornoCobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.Movimento;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.service.financeiro.SecaoExportada.Coluna;
import br.pucminas.eduka.backend.service.privacidade.Formatos;

/**
 * Relatorios financeiros da escola do token (RF19): os numeros da IU19 e a
 * exportacao da IU19-A, em CSV ou em planilha .xlsx com a identidade da
 * escola. So leem: nao participam do pagamento.
 *
 * <p>
 * Recebido e saldo seguem o painel financeiro
 * ({@link DashboardFinanceiroService}): a cobranca PAGA, pelo dia do pagamento,
 * com o valor pago no provedor ou, na baixa manual, o da cobranca; menos os
 * estornos, no periodo do estorno; menos as despesas pagas, pelo dia do
 * pagamento. Cobrancas e despesas lancadas contam pelo vencimento, sem as
 * canceladas: o CSV e a planilha somam o mesmo que a tela.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class RelatorioFinanceiroService {

    private static final String SEM_CURSO = "Sem curso";
    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    private final CobrancaRepository cobrancaRepository;
    private final DespesaRepository despesaRepository;
    private final EstornoCobrancaRepository estornoRepository;
    private final PessoaRepository pessoaRepository;
    private final EscolaRepository escolaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public RelatorioFinanceiroService(CobrancaRepository cobrancaRepository, DespesaRepository despesaRepository,
            EstornoCobrancaRepository estornoRepository, PessoaRepository pessoaRepository,
            EscolaRepository escolaRepository, IsolamentoEscola isolamentoEscola, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.despesaRepository = despesaRepository;
        this.estornoRepository = estornoRepository;
        this.pessoaRepository = pessoaRepository;
        this.escolaRepository = escolaRepository;
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
     * {@link ArquivoCsv.Conjunto}.
     *
     * @throws IllegalArgumentException se nenhum conjunto foi pedido ou o
     *                                  periodo for invalido
     */
    public ArquivoCsv exportar(LocalDate de, LocalDate ate, Set<ArquivoCsv.Conjunto> conjuntos,
            ArquivoCsv.Separador separador) {
        if (conjuntos.isEmpty()) {
            throw new IllegalArgumentException("Escolha ao menos um conjunto de dados para exportar");
        }
        PlanilhaCsv planilha = new PlanilhaCsv(separador);
        for (SecaoExportada secao : secoes(de, ate, conjuntos)) {
            planilha.secao(secao.titulo(), secao.nomesDasColunas());
            secao.linhas().forEach(planilha::linha);
        }
        return new ArquivoCsv("relatorio-financeiro-%s-a-%s.csv".formatted(de, ate), planilha.paraBytes());
    }

    /**
     * A planilha .xlsx da escola do token: a aba "Resumo", com os numeros da
     * tela, e uma aba para cada conjunto, com as mesmas linhas do CSV. Leva o
     * nome, o documento e o contato da escola, e o nome dela no arquivo.
     *
     * @throws IllegalArgumentException se o periodo for invalido
     */
    public ArquivoPlanilha exportarPlanilha(LocalDate de, LocalDate ate) {
        RelatorioFinanceiroResponse relatorio = gerar(de, ate);
        Escola escola = escolaRepository.findById(isolamentoEscola.escolaIdAtual())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Escola nao encontrada"));
        String quando = "Período: %s a %s  ·  Gerado em %s".formatted(Formatos.data(de), Formatos.data(ate),
                Formatos.dataHora(clock.instant()));

        PlanilhaFinanceiraXlsx planilha = new PlanilhaFinanceiraXlsx(
                new PlanilhaFinanceiraXlsx.Escola(escola.getNome(), detalhes(escola)), quando);
        planilha.resumo(relatorio, resumoParaGente(relatorio.resumo()), f -> rotulo(f.forma()));
        secoes(de, ate, EnumSet.allOf(ArquivoCsv.Conjunto.class)).forEach(planilha::aba);
        return new ArquivoPlanilha("relatorio-financeiro-%s-%s-a-%s.xlsx".formatted(slug(escola.getNome()), de, ate),
                planilha.paraBytes());
    }

    /**
     * As linhas de cada conjunto pedido, na ordem de {@link ArquivoCsv.Conjunto}.
     * Le tudo de uma vez: o periodo limitado segura o tamanho.
     */
    private List<SecaoExportada> secoes(LocalDate de, LocalDate ate, Set<ArquivoCsv.Conjunto> conjuntos) {
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

        List<SecaoExportada> secoes = new ArrayList<>();
        if (conjuntos.contains(ArquivoCsv.Conjunto.COBRANCAS)) {
            secoes.add(cobrancas(cobrancas, nomes));
        }
        if (conjuntos.contains(ArquivoCsv.Conjunto.PAGAMENTOS)) {
            secoes.add(pagamentos(pagamentos, nomes));
        }
        if (conjuntos.contains(ArquivoCsv.Conjunto.DESPESAS)) {
            secoes.add(despesas(despesaRepository.lancadasEntre(escolaId, de, ate)));
        }
        return secoes;
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

    private SecaoExportada cobrancas(List<Cobranca> cobrancas, Map<UUID, String> nomes) {
        LocalDate hoje = hoje();
        List<Object[]> linhas = cobrancas.stream()
                .map(c -> new Object[] { nomes.get(c.getAluno().getPessoaId()), nomeDoCurso(c.getMatricula()),
                        rotulo(c.getTipo()), c.getDescricao(), c.getValor(), c.getDataVencimento(),
                        rotulo(StatusCobranca.efetivo(c.getStatus(), c.getDataVencimento(), hoje)),
                        dia(c.getPagaEm()) })
                .toList();
        return new SecaoExportada("Cobranças", "Cobrancas", List.of(Coluna.texto("Aluno"), Coluna.texto("Curso"),
                Coluna.lista("Tipo", rotulos(TipoCobranca.values(), RelatorioFinanceiroService::rotulo)),
                Coluna.texto("Descrição"), Coluna.valor("Valor"), Coluna.texto("Vencimento"),
                Coluna.lista("Situação", rotulos(StatusCobranca.values(), RelatorioFinanceiroService::rotulo)),
                Coluna.texto("Pago em")), linhas, "Nenhuma cobrança vence neste período.");
    }

    private static SecaoExportada pagamentos(List<Recebimento> pagamentos, Map<UUID, String> nomes) {
        List<Object[]> linhas = pagamentos.stream()
                .map(p -> new Object[] { dia(p.getPagaEm()), nomes.get(p.getPessoaId()), p.getDescricao(),
                        p.getValor(), rotulo(p.getForma()), p.getValor().signum() < 0 ? "Estorno" : "Pagamento" })
                .toList();
        List<String> formas = new ArrayList<>(rotulos(FormaPagamento.values(), RelatorioFinanceiroService::rotulo));
        formas.add(rotulo((FormaPagamento) null));
        return new SecaoExportada("Pagamentos", "Pagamentos", List.of(Coluna.texto("Data"), Coluna.texto("Aluno"),
                Coluna.texto("Descrição"), Coluna.valor("Valor"), Coluna.lista("Forma", formas),
                Coluna.lista("Movimento", List.of("Pagamento", "Estorno"))), linhas,
                "Nenhum pagamento ou estorno no período.");
    }

    private static SecaoExportada despesas(List<Despesa> despesas) {
        List<Object[]> linhas = despesas.stream()
                // Despesa lancada antes do responsavel existir sai com a celula vazia.
                .map(d -> new Object[] { d.getDataVencimento(), d.getCategoria().getNome(), d.getDescricao(),
                        d.getResponsavel(), d.getValor(), rotulo(d.getSituacao()), dia(d.getPagaEm()) })
                .toList();
        return new SecaoExportada("Despesas", "Despesas", List.of(Coluna.texto("Vencimento"),
                Coluna.texto("Categoria"), Coluna.texto("Descrição"), Coluna.texto("Responsável"), Coluna.valor("Valor"),
                Coluna.lista("Situação", rotulos(SituacaoDespesa.values(), RelatorioFinanceiroService::rotulo)),
                Coluna.texto("Paga em")), linhas, "Nenhuma despesa vence neste período.");
    }

    private static <E> List<String> rotulos(E[] valores, Function<E, String> rotulo) {
        return Stream.of(valores).map(rotulo).toList();
    }

    /** Os resumos com o tipo e o mes por extenso, como a tela mostra. */
    private static Resumo resumoParaGente(Resumo resumo) {
        return new Resumo(resumo.porCurso(),
                renomear(resumo.porTipo(), grupo -> rotulo(TipoCobranca.valueOf(grupo))),
                renomear(resumo.porMes(), grupo -> mesPorExtenso(YearMonth.parse(grupo))),
                resumo.total());
    }

    private static List<Linha> renomear(List<Linha> linhas, Function<String, String> nome) {
        return linhas.stream()
                .map(l -> new Linha(nome.apply(l.grupo()), l.cobrado(), l.recebido(), l.emAberto(), l.inadimplencia()))
                .toList();
    }

    /** "Agosto de 2026". */
    static String mesPorExtenso(YearMonth mes) {
        String nome = mes.getMonth().getDisplayName(TextStyle.FULL, BRASIL);
        return Character.toUpperCase(nome.charAt(0)) + nome.substring(1) + " de " + mes.getYear();
    }

    /** "CNPJ 12.345.678/0001-90 · Belo Horizonte/MG · contato@escola.com · (31) 3333-4444", so o que houver. */
    private static String detalhes(Escola escola) {
        Endereco endereco = escola.getEndereco();
        String cidade = endereco == null || endereco.cidade() == null ? null
                : endereco.estado() == null ? endereco.cidade() : endereco.cidade() + "/" + endereco.estado();
        String documento = escola.getCnpjCpf() == null ? null
                : (escola.getCnpjCpf().replaceAll("\\D", "").length() == 11 ? "CPF " : "CNPJ ")
                        + Formatos.documento(escola.getCnpjCpf());
        String texto = Stream.of(documento, cidade, escola.getEmail(), Formatos.telefone(escola.getTelefone()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("  ·  "));
        return texto.isEmpty() ? null : texto;
    }

    /** "colegio-sao-jose": sem acento nem espaco, para qualquer sistema abrir. */
    private static String slug(String nome) {
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "escola" : slug;
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
