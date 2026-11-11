package br.pucminas.eduka.backend.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.AceitePrivacidade;
import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;
import br.pucminas.eduka.backend.domain.Aula;
import br.pucminas.eduka.backend.domain.Avaliacao;
import br.pucminas.eduka.backend.domain.CategoriaDespesa;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.Despesa;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.Endereco;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.HorarioTurma;
import br.pucminas.eduka.backend.domain.LogAuditoria;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.ModuloEscola;
import br.pucminas.eduka.backend.domain.Nota;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.OrigemAuditoria;
import br.pucminas.eduka.backend.domain.OrigemPagamento;
import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.PlanoEscola;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.Responsavel;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.SolicitacaoCorrecao;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.StatusSolicitacaoCorrecao;
import br.pucminas.eduka.backend.domain.TipoAvaliacao;
import br.pucminas.eduka.backend.domain.TipoCobranca;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.domain.Turno;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.domain.VinculoProfessor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.repository.AceitePrivacidadeRepository;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.AulaRepository;
import br.pucminas.eduka.backend.repository.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.CategoriaDespesaRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.CursoRepository;
import br.pucminas.eduka.backend.repository.DespesaRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.repository.HorarioTurmaRepository;
import br.pucminas.eduka.backend.repository.LogAuditoriaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.NotaRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.ResponsavelRepository;
import br.pucminas.eduka.backend.repository.SolicitacaoCorrecaoRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.AvisoPrivacidade;
import br.pucminas.eduka.backend.service.CategoriaDespesaService;
import jakarta.persistence.EntityManager;

/**
 * Escola de demonstracao com cara de escola de verdade: o "Colegio Ipe
 * Amarelo", com diretora (MASTER), uma secretaria, um financeiro, 8
 * professores e 50 alunos com responsaveis, mais um ano de historia: o
 * semestre atual e os dois anteriores, com turmas, matriculas, notas,
 * chamadas, mensalidades, despesas e as faturas do Eduka.
 *
 * <p>
 * So existe em dev ({@code MOCK_DATA=true} no .env, que o docker-compose.yml
 * de dev ja liga) e no ambiente de apresentacao (perfis {@code prod,demo}, ver
 * application-demo.properties). Em prod sozinho nao sobe: o perfil
 * {@code prod} exclui a classe e o application-prod.properties fixa
 * {@code app.mock.habilitado=false}.
 * </p>
 *
 * <p>
 * Todas as pessoas sao ficticias ({@link GeradorMock}), com CPF valido; a
 * escola tem CNPJ valido, para a conta de recebimento poder ser ativada no
 * sandbox do provedor. Todos os logins terminam em {@value GeradorMock#DOMINIO}
 * e entram com {@code app.mock.senha} ({@value #SENHA} em dev; no demo, a
 * senha de {@code MOCK_SENHA}).
 * </p>
 *
 * <p>
 * As datas sao relativas ao dia em que o banco e semeado: ha cobrancas do mes
 * atual e dos 12 anteriores, e cada registro tem a data de criacao de quando
 * teria sido lancado (o banco grava "agora"; o seeder corrige depois). Nenhum
 * pagamento e anterior ao lancamento da cobranca, e nada fica com data no
 * futuro. Semeia uma vez so, enquanto a escola nao existe; para recriar, apague
 * o volume do Postgres ({@code docker compose down -v}).
 * </p>
 */
@Component
@Profile("(dev & !prod) | demo")
@ConditionalOnProperty(name = "app.mock.habilitado", havingValue = "true")
@Order(3)
public class MockDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MockDataSeeder.class);

    /** CNPJ com digitos verificadores validos (48.193.276/0001-60). */
    static final String CNPJ = "48193276000160";
    /** CNPJ do mock anterior: um banco com ele tem os mesmos logins e precisa ser recriado. */
    static final String CNPJ_ANTERIOR = "34567891000184";
    static final String SENHA = "Eduka-mock123";

    private static final String LOGIN_SUPER_ADMIN = "admin@eduka.com.br";
    private static final String NOME_ESCOLA = "Colégio Ipê Amarelo";
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final long SEMENTE = 2026L;
    private static final int ALUNOS_POR_TURMA = 10;
    private static final int DIA_VENCIMENTO = 10;
    /** Primeira serie que a escola oferece (6º ano); quem estava abaixo dela entrou depois. */
    private static final int PRIMEIRA_SERIE = 6;
    /** Horas de carga por aula semanal: 160 h viram 4 aulas por semana, 80 h viram 2. */
    private static final int HORAS_POR_AULA_SEMANAL = 40;
    private static final List<String> CONTEUDOS = List.of(
            "Exposição do conteúdo novo e exercícios em sala",
            "Correção da lista de exercícios e dúvidas",
            "Atividade em grupo e discussão dos resultados",
            "Revisão do capítulo e exercícios do livro",
            "Leitura orientada e resumo do texto",
            "Resolução de questões de vestibular e ENEM");
    /** Feriados sem aula (os de data fixa). */
    private static final Set<MonthDay> FERIADOS = Set.of(MonthDay.of(4, 21), MonthDay.of(5, 1),
            MonthDay.of(9, 7), MonthDay.of(10, 12), MonthDay.of(11, 2), MonthDay.of(11, 15), MonthDay.of(11, 20),
            MonthDay.of(12, 8));

    private record Funcionario(String nome, PerfilUsuario perfil) {
    }

    private static final List<Funcionario> EQUIPE = List.of(
            new Funcionario("Helena Martins Duarte", PerfilUsuario.MASTER),
            new Funcionario("Patrícia Lopes Andrade", PerfilUsuario.SECRETARIA),
            new Funcionario("Marcos Vinícius Teixeira", PerfilUsuario.FINANCEIRO));

    private record ProfessorMock(String nome, String formacao, VinculoProfessor vinculo, LocalDate admissao,
            List<String> disciplinas) {
    }

    private static final List<ProfessorMock> PROFESSORES = List.of(
            new ProfessorMock("Ana Paula Ribeiro", "Licenciatura em Matemática", VinculoProfessor.CLT_40H,
                    LocalDate.of(2019, 2, 4), List.of("MAT")),
            new ProfessorMock("Carlos Eduardo Moreira", "Licenciatura em Letras - Português",
                    VinculoProfessor.CLT_40H, LocalDate.of(2017, 2, 6), List.of("POR")),
            new ProfessorMock("Juliana Castro Pereira", "Licenciatura em Ciências Biológicas",
                    VinculoProfessor.CLT_30H, LocalDate.of(2021, 2, 1), List.of("CIE", "BIO")),
            new ProfessorMock("Fernando Almeida Rocha", "Licenciatura em Física", VinculoProfessor.CLT_20H,
                    LocalDate.of(2022, 7, 25), List.of("FIS", "QUI")),
            new ProfessorMock("Luciana Barbosa Santos", "Licenciatura em História", VinculoProfessor.CLT_20H,
                    LocalDate.of(2020, 2, 3), List.of("HIS")),
            new ProfessorMock("Ricardo Gomes Cardoso", "Licenciatura em Geografia", VinculoProfessor.HORISTA,
                    LocalDate.of(2023, 2, 6), List.of("GEO")),
            new ProfessorMock("Beatriz Carvalho Lima", "Licenciatura em Letras - Inglês", VinculoProfessor.HORISTA,
                    LocalDate.of(2024, 2, 5), List.of("ING")),
            new ProfessorMock("Thiago Souza Mendes", "Licenciatura em Educação Física", VinculoProfessor.CLT_20H,
                    LocalDate.of(2018, 2, 5), List.of("EDF")));

    /** Codigo -> nome e carga horaria, na ordem em que sao criadas. */
    private static final Map<String, Map.Entry<String, Integer>> DISCIPLINAS = new LinkedHashMap<>();

    static {
        DISCIPLINAS.put("MAT", Map.entry("Matemática", 160));
        DISCIPLINAS.put("POR", Map.entry("Língua Portuguesa", 160));
        DISCIPLINAS.put("CIE", Map.entry("Ciências", 120));
        DISCIPLINAS.put("BIO", Map.entry("Biologia", 80));
        DISCIPLINAS.put("FIS", Map.entry("Física", 80));
        DISCIPLINAS.put("QUI", Map.entry("Química", 80));
        DISCIPLINAS.put("HIS", Map.entry("História", 80));
        DISCIPLINAS.put("GEO", Map.entry("Geografia", 80));
        DISCIPLINAS.put("ING", Map.entry("Língua Inglesa", 80));
        DISCIPLINAS.put("EDF", Map.entry("Educação Física", 80));
    }

    private static final List<String> GRADE_FUNDAMENTAL = List.of("MAT", "POR", "CIE", "HIS", "GEO", "ING", "EDF");
    private static final List<String> GRADE_MEDIO = List.of("MAT", "POR", "BIO", "FIS", "QUI", "HIS", "GEO", "ING",
            "EDF");

    /**
     * Turma do semestre atual. A serie conta do 6º ano (6) ao 3º ano do medio
     * (12); no ano anterior, os mesmos alunos estavam uma serie abaixo.
     */
    private record TurmaMock(int serie, String letra, Turno turno) {
    }

    private static final List<TurmaMock> TURMAS = List.of(
            new TurmaMock(6, "A", Turno.MATUTINO),
            new TurmaMock(8, "A", Turno.MATUTINO),
            new TurmaMock(9, "B", Turno.VESPERTINO),
            new TurmaMock(10, "A", Turno.MATUTINO),
            new TurmaMock(12, "A", Turno.MATUTINO));

    /** Um semestre letivo: o primeiro vai de fevereiro a julho; o segundo, de agosto a dezembro. */
    private record Semestre(int ano, boolean primeiro) {

        String nome() {
            return ano + (primeiro ? "/1" : "/2");
        }

        LocalDate inicio() {
            return primeiro ? LocalDate.of(ano, 2, 2) : LocalDate.of(ano, 8, 3);
        }

        LocalDate fim() {
            return primeiro ? LocalDate.of(ano, 7, 3) : LocalDate.of(ano, 12, 18);
        }

        /** Meses de mensalidade: janeiro a julho no primeiro, agosto a dezembro no segundo. */
        List<Integer> meses() {
            return primeiro ? IntStream.rangeClosed(1, 7).boxed().toList()
                    : IntStream.rangeClosed(8, 12).boxed().toList();
        }

        Semestre anterior() {
            return primeiro ? new Semestre(ano - 1, false) : new Semestre(ano, true);
        }

        static Semestre de(LocalDate dia) {
            return new Semestre(dia.getYear(), dia.getMonthValue() < 8);
        }
    }

    /** Pais de um ou mais alunos: irmaos dividem endereco e responsaveis. */
    private static final class Familia {
        private final String sobrenomeMae;
        private final String sobrenomePai;
        private final String[] endereco;
        private final Map<Pessoa, String> responsaveis = new LinkedHashMap<>();
        private Pessoa financeiro;
        private boolean temIrmao;
        private boolean datada;

        private Familia(String sobrenomeMae, String sobrenomePai, String[] endereco) {
            this.sobrenomeMae = sobrenomeMae;
            this.sobrenomePai = sobrenomePai;
            this.endereco = endereco;
        }
    }

    /** Aluno com a matricula de cada semestre (nula antes de ele entrar na escola). */
    private static final class AlunoMock {
        private final Aluno aluno;
        private final TurmaMock turma;
        private final UUID financeiroId;
        private final double desempenho;
        private final int entrada;
        /** Dia da primeira matricula: o do cadastro. */
        private final LocalDate entrouEm;
        private final MatriculaTurma[] matriculas;

        private AlunoMock(Aluno aluno, TurmaMock turma, UUID financeiroId, double desempenho, int entrada,
                LocalDate entrouEm, int semestres) {
            this.aluno = aluno;
            this.turma = turma;
            this.financeiroId = financeiroId;
            this.desempenho = desempenho;
            this.entrada = entrada;
            this.entrouEm = entrouEm;
            this.matriculas = new MatriculaTurma[semestres];
        }
    }

    /** Ids da equipe criada na primeira transacao, para assinar as atividades. */
    private record Equipe(Usuario master, UUID secretaria, UUID financeiro, UUID superAdmin,
            List<Pessoa> pessoas, List<Usuario> usuarios) {
    }

    /**
     * Estado de uma semeadura: o dia de referencia, o gerador e o que so pode
     * ser gravado no fim (datas retroativas e o registro de atividades).
     */
    private static final class Contexto {
        private final LocalDate hoje;
        private final Instant agora;
        private final GeradorMock gerador;
        private final String senhaHash;
        /** Primeiro dia do mes de 12 meses atras: a cobranca mais antiga vence nele ou depois. */
        private final LocalDate janela;
        /** Semestres do mais antigo ao atual. */
        private final List<Semestre> semestres;
        /** Quando a escola comecou a usar o Eduka: um pouco antes do semestre mais antigo. */
        private final LocalDate inicioEscola;
        private final Map<String, List<Object[]>> datas = new LinkedHashMap<>();
        private final List<LogAuditoria> atividades = new ArrayList<>();
        private UUID escolaId;
        private Equipe equipe;

        private Contexto(LocalDate hoje, Instant agora, GeradorMock gerador, String senhaHash) {
            this.hoje = hoje;
            this.agora = agora;
            this.gerador = gerador;
            this.senhaHash = senhaHash;
            this.janela = hoje.withDayOfMonth(1).minusMonths(12);
            Semestre atual = Semestre.de(hoje);
            Semestre anterior = atual.anterior();
            Semestre antes = anterior.anterior();
            this.semestres = List.of(antes, anterior, atual);
            this.inicioEscola = antes.inicio().minusMonths(4).withDayOfMonth(1);
        }

        int atual() {
            return semestres.size() - 1;
        }

        int anoAtual() {
            return semestres.get(atual()).ano();
        }

        /** Primeiro semestre do ano letivo atual: quando os alunos novos do ano entraram. */
        int inicioDoAno() {
            return semestres.get(1).ano() == anoAtual() ? 1 : atual();
        }

        boolean passou(Instant instante) {
            return instante != null && instante.isBefore(agora);
        }

        /** Instante do dia na hora dada, num minuto sorteado. */
        Instant em(LocalDate dia, int horaMinima, int horaMaxima) {
            return dia.atTime(gerador.entre(horaMinima, horaMaxima), gerador.entre(0, 59)).atZone(ZONA)
                    .toInstant();
        }

        /** Corrige a data de criacao no fim; o que cairia no futuro fica com a do banco (agora). */
        void datar(String tabela, String colunaId, String colunaData, Object id, Instant quando) {
            if (!passou(quando)) {
                return;
            }
            String sql = "UPDATE %s SET %s = ? WHERE %s = ?".formatted(tabela, colunaData, colunaId);
            datas.computeIfAbsent(sql, chave -> new ArrayList<>()).add(new Object[] { Timestamp.from(quando), id });
        }

        void datar(String tabela, String colunaId, Object id, Instant quando) {
            datar(tabela, colunaId, "CRIADO_EM", id, quando);
        }

        void registrar(UUID usuarioId, OperacaoAuditoria operacao, EntidadeAuditada entidade, UUID registroId,
                Instant quando) {
            registrar(usuarioId == null ? OrigemAuditoria.SISTEMA : OrigemAuditoria.USUARIO, usuarioId, operacao,
                    entidade, registroId, quando);
        }

        void registrar(OrigemAuditoria origem, UUID usuarioId, OperacaoAuditoria operacao,
                EntidadeAuditada entidade, UUID registroId, Instant quando) {
            if (!passou(quando)) {
                return;
            }
            atividades.add(LogAuditoria.builder()
                    .escolaId(escolaId)
                    .usuarioId(usuarioId)
                    .origem(origem)
                    .operacao(operacao)
                    .entidade(entidade)
                    .registroId(registroId)
                    .dataHora(quando)
                    .build());
        }
    }

    private final EscolaRepository escolaRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProfessorRepository professorRepository;
    private final PeriodoLetivoRepository periodoLetivoRepository;
    private final CursoRepository cursoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final TurmaRepository turmaRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final AlunoRepository alunoRepository;
    private final ResponsavelRepository responsavelRepository;
    private final AlunoResponsavelRepository alunoResponsavelRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final NotaRepository notaRepository;
    private final HorarioTurmaRepository horarioRepository;
    private final AulaRepository aulaRepository;
    private final CobrancaRepository cobrancaRepository;
    private final PagamentoCobrancaRepository pagamentoRepository;
    private final CategoriaDespesaService categoriaDespesaService;
    private final CategoriaDespesaRepository categoriaDespesaRepository;
    private final DespesaRepository despesaRepository;
    private final FaturaPlataformaRepository faturaRepository;
    private final SolicitacaoCorrecaoRepository solicitacaoRepository;
    private final AceitePrivacidadeRepository aceiteRepository;
    private final LogAuditoriaRepository logAuditoriaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final String senha;

    public MockDataSeeder(EscolaRepository escolaRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, UsuarioRepository usuarioRepository,
            ProfessorRepository professorRepository, PeriodoLetivoRepository periodoLetivoRepository,
            CursoRepository cursoRepository, DisciplinaRepository disciplinaRepository,
            TurmaRepository turmaRepository, AlocacaoProfessorRepository alocacaoRepository,
            AlunoRepository alunoRepository, ResponsavelRepository responsavelRepository,
            AlunoResponsavelRepository alunoResponsavelRepository, MatriculaTurmaRepository matriculaRepository,
            AvaliacaoRepository avaliacaoRepository, NotaRepository notaRepository,
            HorarioTurmaRepository horarioRepository, AulaRepository aulaRepository,
            CobrancaRepository cobrancaRepository, PagamentoCobrancaRepository pagamentoRepository,
            CategoriaDespesaService categoriaDespesaService, CategoriaDespesaRepository categoriaDespesaRepository,
            DespesaRepository despesaRepository, FaturaPlataformaRepository faturaRepository,
            SolicitacaoCorrecaoRepository solicitacaoRepository, AceitePrivacidadeRepository aceiteRepository,
            LogAuditoriaRepository logAuditoriaRepository, PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate, EntityManager entityManager, Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${app.mock.senha:" + SENHA + "}") String senha) {
        this.escolaRepository = escolaRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.usuarioRepository = usuarioRepository;
        this.professorRepository = professorRepository;
        this.periodoLetivoRepository = periodoLetivoRepository;
        this.cursoRepository = cursoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.turmaRepository = turmaRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.alunoRepository = alunoRepository;
        this.responsavelRepository = responsavelRepository;
        this.alunoResponsavelRepository = alunoResponsavelRepository;
        this.matriculaRepository = matriculaRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.notaRepository = notaRepository;
        this.horarioRepository = horarioRepository;
        this.aulaRepository = aulaRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.categoriaDespesaService = categoriaDespesaService;
        this.categoriaDespesaRepository = categoriaDespesaRepository;
        this.despesaRepository = despesaRepository;
        this.faturaRepository = faturaRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.aceiteRepository = aceiteRepository;
        this.logAuditoriaRepository = logAuditoriaRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.clock = clock;
        this.senha = senha;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Duas transacoes, como no DevDataSeeder: a escola e a equipe nascem sem
     * escola no contexto; o resto precisa da escola nova como tenant, e o
     * Hibernate so resolve o tenant ao abrir a sessao.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (escolaRepository.existsByCnpjCpf(CNPJ)) {
            log.info("Mock: {} ja existe, nada a semear", NOME_ESCOLA);
            return;
        }
        if (escolaRepository.existsByCnpjCpf(CNPJ_ANTERIOR)) {
            // Os logins sao os mesmos: semear de novo bateria no login unico.
            log.warn("Mock: o banco tem a versao antiga do {}. Para semear a nova, apague o volume do Postgres "
                    + "(docker compose down -v) e suba de novo", NOME_ESCOLA);
            return;
        }
        Contexto contexto = new Contexto(LocalDate.now(clock.withZone(ZONA)), clock.instant(),
                new GeradorMock(SEMENTE), passwordEncoder.encode(senha));

        Equipe equipe = transactionTemplate.execute(status -> criarEscolaEEquipe(contexto));
        contexto.equipe = equipe;
        contexto.escolaId = equipe.master().getEscolaId();
        comoEscola(equipe.master(), () -> transactionTemplate.executeWithoutResult(status -> semear(contexto)));

        // A senha padrao de dev esta no README; uma senha propria (demo) nunca vai para o log.
        // Sem o login da diretora: login e dado pessoal, mesmo no mock. Os logins estao no README.
        log.info("Mock: {} semeado. Logins em @{} com a senha {}", NOME_ESCOLA,
                GeradorMock.DOMINIO, SENHA.equals(senha) ? SENHA : "de MOCK_SENHA");
    }

    private Equipe criarEscolaEEquipe(Contexto c) {
        Escola escola = Escola.cadastrar(NOME_ESCOLA, CNPJ,
                new Endereco("Rua dos Timbiras", "1560", "Funcionários", "Belo Horizonte", "MG", "30140061"),
                "secretaria@" + GeradorMock.DOMINIO, "(31) 3261-4500", PlanoEscola.ENTERPRISE, true);
        escola.atualizarAssinatura(c.hoje.plusYears(2), 500, EnumSet.allOf(ModuloEscola.class));
        UUID escolaId = escolaRepository.save(escola).getId();

        Usuario master = null;
        UUID secretaria = null;
        UUID financeiro = null;
        List<Pessoa> pessoas = new ArrayList<>();
        List<Usuario> usuarios = new ArrayList<>();
        for (Funcionario funcionario : EQUIPE) {
            Pessoa pessoa = criarAdulto(escolaId, funcionario.nome(), c.gerador, PapelPessoa.FUNCIONARIO);
            Usuario usuario = criarLogin(pessoa, funcionario.perfil(), c.senhaHash);
            pessoas.add(pessoa);
            usuarios.add(usuario);
            switch (funcionario.perfil()) {
                case MASTER -> master = usuario;
                case SECRETARIA -> secretaria = usuario.getId();
                case FINANCEIRO -> financeiro = usuario.getId();
                default -> throw new IllegalStateException("Perfil fora da equipe: " + funcionario.perfil());
            }
        }
        // Quem lanca as faturas do Eduka; sem o Super Admin (perfil demo sem ele), a diretora.
        UUID masterId = master.getId();
        UUID superAdmin = usuarioRepository.findByLogin(LOGIN_SUPER_ADMIN).map(Usuario::getId).orElse(masterId);
        return new Equipe(master, secretaria, financeiro, superAdmin, pessoas, usuarios);
    }

    private void semear(Contexto c) {
        Escola escola = escolaRepository.getReferenceById(c.escolaId);
        datarEquipe(c);

        Curso fundamental = cursoRepository.save(Curso.builder()
                .escola(escola).nome("Ensino Fundamental II").codigo("EF2").build());
        Curso medio = cursoRepository.save(Curso.builder().escola(escola).nome("Ensino Médio").codigo("EM").build());

        Map<String, Disciplina> disciplinas = new LinkedHashMap<>();
        DISCIPLINAS.forEach((codigo, nomeECarga) -> disciplinas.put(codigo, disciplinaRepository.save(
                Disciplina.builder().escola(escola).nome(nomeECarga.getKey()).codigo(codigo)
                        .cargaHoraria(nomeECarga.getValue()).build())));

        Map<String, Professor> professorPorDisciplina = new LinkedHashMap<>();
        for (ProfessorMock mock : PROFESSORES) {
            Professor professor = criarProfessor(c, mock);
            mock.disciplinas().forEach(codigo -> professorPorDisciplina.put(codigo, professor));
        }

        // Turmas e professores de cada semestre: as do semestre atual estao ativas.
        List<Map<TurmaMock, List<AlocacaoProfessor>>> alocacoes = new ArrayList<>();
        for (int k = 0; k < c.semestres.size(); k++) {
            Semestre semestre = c.semestres.get(k);
            boolean atual = k == c.atual();
            PeriodoLetivo periodo = periodoLetivoRepository.save(PeriodoLetivo.builder()
                    .escola(escola).nome(semestre.nome()).dataInicio(semestre.inicio()).dataFim(semestre.fim())
                    .encerradoEm(atual ? null : semestre.fim().plusDays(3))
                    .build());
            Map<TurmaMock, List<AlocacaoProfessor>> doSemestre = new LinkedHashMap<>();
            for (TurmaMock turmaMock : TURMAS) {
                int serie = serieEm(turmaMock, semestre, c.anoAtual());
                if (serie < PRIMEIRA_SERIE) {
                    continue;
                }
                List<String> grade = medio(serie) ? GRADE_MEDIO : GRADE_FUNDAMENTAL;
                String codigo = codigoTurma(serie, turmaMock.letra())
                        + (atual ? "" : "-" + semestre.nome().replace('/', '.'));
                Turma turma = criarTurma(escola, periodo, medio(serie) ? medio : fundamental,
                        nomeTurma(serie, turmaMock.letra()), codigo, turmaMock.turno(), atual,
                        grade.stream().map(disciplinas::get).toList());
                doSemestre.put(turmaMock, grade.stream()
                        .map(disciplina -> alocacaoRepository.save(AlocacaoProfessor.builder()
                                .escolaId(c.escolaId).professor(professorPorDisciplina.get(disciplina))
                                .turma(turma).disciplina(disciplinas.get(disciplina)).build()))
                        .toList());
            }
            alocacoes.add(doSemestre);
        }

        List<AlunoMock> alunos = criarAlunos(c, alocacoes);

        // Avaliacoes de todos os semestres; as que ja passaram tem nota.
        for (int k = 0; k < c.semestres.size(); k++) {
            int semestre = k;
            alocacoes.get(k).forEach((turmaMock, daTurma) -> {
                List<AlunoMock> turma = alunosDaTurma(alunos, turmaMock, semestre);
                daTurma.forEach(alocacao -> avaliar(c, alocacao, c.semestres.get(semestre), turma));
            });
        }

        // Grade semanal e chamadas do semestre atual.
        Semestre atual = c.semestres.get(c.atual());
        List<GradeMock.Pedido<AlocacaoProfessor>> pedidos = new ArrayList<>();
        Map<Long, List<AlunoMock>> alunosPorTurma = new LinkedHashMap<>();
        alocacoes.get(c.atual()).forEach((turmaMock, daTurma) -> {
            Turma turma = daTurma.getFirst().getTurma();
            alunosPorTurma.put(turma.getId(), alunosDaTurma(alunos, turmaMock, c.atual()));
            daTurma.forEach(alocacao -> pedidos.add(new GradeMock.Pedido<>(alocacao, turma.getId(),
                    alocacao.getProfessor().getId(), turma.getTurno(),
                    alocacao.getDisciplina().getCargaHoraria() / HORAS_POR_AULA_SEMANAL)));
        });
        List<GradeMock.Aula<AlocacaoProfessor>> grade = GradeMock.montar(pedidos);
        horarioRepository.saveAll(grade.stream()
                .map(aula -> HorarioTurma.builder()
                        .escolaId(c.escolaId)
                        .alocacao(aula.alocacao())
                        .diaSemana(aula.dia())
                        .horaInicio(aula.inicio())
                        .horaFim(aula.fim())
                        .build())
                .toList());
        registrarChamadas(c, grade, alunosPorTurma, atual);
        deixarNotasPorLancar(c, pedidos, alunosPorTurma, atual);

        List<Cobranca> cobrancas = lancarCobrancas(c, alunos);
        lancarDespesas(c, mensalidadesPorMes(cobrancas));
        lancarFaturas(c);
        abrirSolicitacoes(c, alunos);
        aceitarAvisoDePrivacidade(c, professorPorDisciplina.values());

        logAuditoriaRepository.saveAll(c.atividades);
        gravarDatas(c);
    }

    // ---- Pessoas e logins ----

    private Pessoa criarAdulto(UUID escolaId, String nome, GeradorMock gerador, PapelPessoa papel) {
        String[] endereco = gerador.endereco();
        LocalDate nascimento = LocalDate.of(gerador.entre(1972, 1996), gerador.entre(1, 12), gerador.entre(1, 28));
        return criarPessoa(escolaId, nome, gerador.cpf(), nascimento, gerador.email(nome), gerador.celular(),
                endereco, papel);
    }

    private Pessoa criarPessoa(UUID escolaId, String nome, String cpf, LocalDate nascimento, String email,
            String telefone, String[] endereco, PapelPessoa papel) {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(nome)
                .cpf(cpf)
                .dataNascimento(nascimento.toString())
                .email(email)
                .telefone(telefone)
                .logradouro(endereco[0])
                .numero(endereco[1])
                .bairro(endereco[2])
                .cidade("Belo Horizonte")
                .estado("MG")
                .cep(endereco[3])
                .ativo(true)
                .build());
        pessoaPapelRepository.save(PessoaPapel.builder().pessoaId(pessoa.getId()).papel(papel).build());
        return pessoa;
    }

    /** O login e o e-mail da pessoa; a senha ja vem com hash (BCrypt e lento para 60 logins). */
    private Usuario criarLogin(Pessoa pessoa, PerfilUsuario perfil, String senhaHash) {
        return usuarioRepository.save(Usuario.builder()
                .escolaId(pessoa.getEscolaId())
                .pessoaId(pessoa.getId())
                .login(pessoa.getEmail())
                .senhaHash(senhaHash)
                .perfil(perfil)
                .ativo(true)
                .build());
    }

    /** A diretora entrou no dia em que a escola comecou no Eduka e cadastrou a equipe logo depois. */
    private void datarEquipe(Contexto c) {
        Instant inicio = c.em(c.inicioEscola, 9, 9);
        jdbcTemplate.update("UPDATE ESCOLA SET CRIADO_EM = ?, INICIO_VIGENCIA = ? WHERE ID_ESCOLA = ?",
                Timestamp.from(inicio), java.sql.Date.valueOf(c.inicioEscola), c.escolaId);
        Equipe equipe = c.equipe;
        for (int i = 0; i < equipe.usuarios().size(); i++) {
            Usuario usuario = equipe.usuarios().get(i);
            Instant quando = inicio.plus(Duration.ofMinutes(20L + 15L * i));
            c.datar("PESSOA", "ID_PESSOA", equipe.pessoas().get(i).getId(), quando);
            c.datar("USUARIO", "ID_USUARIO", usuario.getId(), quando);
            if (usuario.getPerfil() != PerfilUsuario.MASTER) {
                c.registrar(equipe.master().getId(), OperacaoAuditoria.CRIAR, EntidadeAuditada.USUARIO,
                        usuario.getId(), quando);
            }
        }
    }

    private Professor criarProfessor(Contexto c, ProfessorMock mock) {
        Pessoa pessoa = criarAdulto(c.escolaId, mock.nome(), c.gerador, PapelPessoa.PROFESSOR);
        Usuario usuario = criarLogin(pessoa, PerfilUsuario.PROFESSOR, c.senhaHash);
        Professor professor = professorRepository.save(Professor.builder()
                .escolaId(c.escolaId)
                .pessoaId(pessoa.getId())
                .usuarioId(usuario.getId())
                .formacao(mock.formacao())
                .dataAdmissao(mock.admissao())
                .vinculo(mock.vinculo())
                .ativo(true)
                .build());
        Instant quando = c.em(c.inicioEscola.plusDays(2), 10, 16);
        c.datar("PESSOA", "ID_PESSOA", pessoa.getId(), quando);
        c.datar("USUARIO", "ID_USUARIO", usuario.getId(), quando);
        c.datar("PROFESSOR", "ID_PROFESSOR", professor.getId(), quando);
        c.registrar(c.equipe.secretaria(), OperacaoAuditoria.CRIAR, EntidadeAuditada.PROFESSOR, professor.getId(),
                quando);
        return professor;
    }

    /**
     * 10 alunos em cada turma atual. A maioria ja estudava na escola no
     * semestre mais antigo; alguns entraram no comeco do ano e outros neste
     * semestre. O 6º ano inteiro entrou no comeco do ano (a escola comeca no
     * 6º ano).
     */
    private List<AlunoMock> criarAlunos(Contexto c, List<Map<TurmaMock, List<AlocacaoProfessor>>> alocacoes) {
        List<Familia> familias = new ArrayList<>();
        List<AlunoMock> alunos = new ArrayList<>();
        int sequencia = 1;
        for (TurmaMock turmaMock : TURMAS) {
            int primeiro = 0;
            while (serieEm(turmaMock, c.semestres.get(primeiro), c.anoAtual()) < PRIMEIRA_SERIE) {
                primeiro++;
            }
            for (int i = 0; i < ALUNOS_POR_TURMA; i++) {
                double sorteio = c.gerador.random().nextDouble();
                int entrada = Math.max(primeiro, sorteio < 0.8 ? 0 : sorteio < 0.93 ? c.inicioDoAno() : c.atual());
                Familia familia = familiaDoProximoAluno(c.escolaId, familias, c.gerador);
                AlunoMock aluno = criarAluno(c, turmaMock, familia, entrada, sequencia++);
                for (int k = entrada; k < c.semestres.size(); k++) {
                    matricular(c, aluno, k, alocacoes.get(k).get(turmaMock).getFirst().getTurma());
                }
                alunos.add(aluno);
            }
        }
        return alunos;
    }

    /**
     * Uma familia nova, ou, de vez em quando, uma ja criada em outra turma:
     * irmaos em series diferentes, com os mesmos responsaveis.
     */
    private Familia familiaDoProximoAluno(UUID escolaId, List<Familia> familias, GeradorMock gerador) {
        if (!familias.isEmpty() && gerador.chance(0.12)) {
            Familia candidata = familias.get(gerador.random().nextInt(familias.size()));
            if (!candidata.temIrmao) {
                candidata.temIrmao = true;
                return candidata;
            }
        }
        Familia familia = new Familia(gerador.sobrenome(), gerador.sobrenome(), gerador.endereco());
        double composicao = gerador.random().nextDouble();
        if (composicao < 0.95) {
            String mae = gerador.mulher() + " " + gerador.sobrenome() + " " + familia.sobrenomeMae;
            familia.responsaveis.put(criarResponsavel(escolaId, mae, familia, gerador), "Mãe");
        }
        if (composicao < 0.80 || composicao >= 0.95) {
            String pai = gerador.homem() + " " + gerador.sobrenome() + " " + familia.sobrenomePai;
            familia.responsaveis.put(criarResponsavel(escolaId, pai, familia, gerador), "Pai");
        }
        List<Pessoa> pessoas = new ArrayList<>(familia.responsaveis.keySet());
        familia.financeiro = pessoas.size() > 1 && gerador.chance(0.4) ? pessoas.get(1) : pessoas.get(0);
        familias.add(familia);
        return familia;
    }

    private Pessoa criarResponsavel(UUID escolaId, String nome, Familia familia, GeradorMock gerador) {
        LocalDate nascimento = LocalDate.of(gerador.entre(1972, 1990), gerador.entre(1, 12), gerador.entre(1, 28));
        Pessoa pessoa = criarPessoa(escolaId, nome, gerador.cpf(), nascimento, gerador.email(nome),
                gerador.celular(), familia.endereco, PapelPessoa.RESPONSAVEL);
        responsavelRepository.save(Responsavel.builder().escolaId(escolaId).pessoaId(pessoa.getId()).build());
        return pessoa;
    }

    private AlunoMock criarAluno(Contexto c, TurmaMock turmaMock, Familia familia, int entrada, int sequencia) {
        GeradorMock gerador = c.gerador;
        boolean menina = gerador.chance(0.5);
        String nome = gerador.nomeDeFilho(menina, familia.sobrenomeMae, familia.sobrenomePai);
        int idade = turmaMock.serie() + 5;
        LocalDate nascimento = LocalDate.of(c.anoAtual() - idade, 1, 1).minusDays(gerador.entre(0, 364));
        String telefone = idade >= 14 ? gerador.celular() : null;
        Pessoa pessoa = criarPessoa(c.escolaId, nome, gerador.cpf(), nascimento, gerador.email(nome), telefone,
                familia.endereco, PapelPessoa.ALUNO);
        Usuario usuario = criarLogin(pessoa, PerfilUsuario.ALUNO, c.senhaHash);

        Aluno aluno = alunoRepository.save(Aluno.builder()
                .escolaId(c.escolaId)
                .pessoaId(pessoa.getId())
                .usuarioId(usuario.getId())
                .matricula("%d-%04d".formatted(c.semestres.get(entrada).ano(), sequencia))
                .genero(menina ? "Feminino" : "Masculino")
                .build());
        familia.responsaveis.forEach((responsavel, parentesco) -> alunoResponsavelRepository.save(
                AlunoResponsavel.builder()
                        .id(new AlunoResponsavelId(aluno.getId(), responsavel.getId()))
                        .parentesco(parentesco)
                        .financeiro(responsavel == familia.financeiro)
                        .build()));

        // Cadastro no dia da primeira matricula, de manha; os pais junto com o primeiro filho.
        LocalDate entrouEm = primeiraMatricula(c, entrada);
        Instant cadastro = c.em(entrouEm, 8, 11);
        c.datar("PESSOA", "ID_PESSOA", pessoa.getId(), cadastro);
        c.datar("USUARIO", "ID_USUARIO", usuario.getId(), cadastro);
        c.datar("alunos", "id_aluno", aluno.getId(), cadastro);
        if (!familia.datada) {
            familia.datada = true;
            familia.responsaveis.keySet().forEach(responsavel -> c.datar("PESSOA", "ID_PESSOA",
                    responsavel.getId(), cadastro.minus(Duration.ofMinutes(10))));
        }
        c.registrar(c.equipe.secretaria(), OperacaoAuditoria.CRIAR, EntidadeAuditada.ALUNO, aluno.getId(),
                cadastro);

        // Media do aluno: a maioria entre 6 e 9, alguns abaixo de 6 (recuperacao).
        double desempenho = Math.clamp(7.2 + gerador.random().nextGaussian() * 1.3, 3.5, 9.8);
        return new AlunoMock(aluno, turmaMock, familia.financeiro.getId(), desempenho, entrada, entrouEm,
                c.semestres.size());
    }

    /** Quem entra se matricula de 20 a 45 dias antes da primeira mensalidade. */
    private static LocalDate primeiraMatricula(Contexto c, int entrada) {
        Semestre semestre = c.semestres.get(entrada);
        LocalDate primeiraMensalidade = LocalDate.of(semestre.ano(), semestre.meses().getFirst(), DIA_VENCIMENTO);
        return primeiraMensalidade.minusDays(c.gerador.entre(20, 45));
    }

    /** Matricula no semestre {@code k}: concluida nos que passaram, ativa no atual. */
    private void matricular(Contexto c, AlunoMock aluno, int k, Turma turma) {
        boolean atual = k == c.atual();
        Semestre semestre = c.semestres.get(k);
        // Rematricula logo depois do fim do semestre anterior.
        LocalDate data = k == aluno.entrada ? aluno.entrouEm
                : c.semestres.get(k - 1).fim().plusDays(c.gerador.entre(1, 10));
        Instant concluida = c.em(semestre.fim().plusDays(3), 10, 11);
        MatriculaTurma matricula = matriculaRepository.save(MatriculaTurma.builder()
                .escolaId(c.escolaId)
                .aluno(aluno.aluno)
                .turma(turma)
                .status(atual ? StatusMatricula.ATIVA : StatusMatricula.CONCLUIDA)
                .dataMatricula(data)
                .atualizadoEm(atual ? null : concluida)
                .build());
        aluno.matriculas[k] = matricula;
        Instant criada = c.em(data, 9, 16);
        c.datar("MATRICULA_TURMA", "ID_MATRICULA", matricula.getId(), criada);
        c.registrar(c.equipe.secretaria(), OperacaoAuditoria.CRIAR, EntidadeAuditada.MATRICULA, matricula.getId(),
                criada);
        if (!atual) {
            c.registrar(c.equipe.secretaria(), OperacaoAuditoria.ALTERAR_SITUACAO, EntidadeAuditada.MATRICULA,
                    matricula.getId(), concluida);
        }
    }

    private static List<AlunoMock> alunosDaTurma(List<AlunoMock> alunos, TurmaMock turma, int semestre) {
        return alunos.stream().filter(a -> a.turma == turma && a.matriculas[semestre] != null).toList();
    }

    // ---- Turmas e notas ----

    private static int serieEm(TurmaMock turma, Semestre semestre, int anoAtual) {
        return turma.serie() - (anoAtual - semestre.ano());
    }

    private static boolean medio(int serie) {
        return serie >= 10;
    }

    private static String nomeTurma(int serie, String letra) {
        return "%dº ano %s".formatted(medio(serie) ? serie - 9 : serie, letra);
    }

    private static String codigoTurma(int serie, String letra) {
        return medio(serie) ? "EM-%d%s".formatted(serie - 9, letra) : "EF2-%d%s".formatted(serie, letra);
    }

    /** Mensalidade do ano: reajuste de cerca de 8% na virada. */
    private static BigDecimal mensalidade(int serie, boolean anoAnterior) {
        if (medio(serie)) {
            return new BigDecimal(anoAnterior ? "1550.00" : "1680.00");
        }
        return new BigDecimal(anoAnterior ? "1340.00" : "1450.00");
    }

    private Turma criarTurma(Escola escola, PeriodoLetivo periodo, Curso curso, String nome, String codigo,
            Turno turno, boolean ativa, List<Disciplina> grade) {
        Set<Disciplina> disciplinas = new LinkedHashSet<>(grade);
        // Flush ja grava TURMA_DISCIPLINA: a FK da alocacao exige a disciplina na grade da turma.
        return turmaRepository.saveAndFlush(Turma.builder()
                .escola(escola)
                .periodoLetivo(periodo)
                .curso(curso)
                .nome(nome)
                .codigo(codigo)
                .vagas(35)
                .turno(turno)
                .ativa(ativa)
                .disciplinas(disciplinas)
                .build());
    }

    /** Tres avaliacoes por disciplina no semestre; as que ja passaram tem nota publicada. */
    private void avaliar(Contexto c, AlocacaoProfessor alocacao, Semestre semestre, List<AlunoMock> alunos) {
        avaliar(c, alocacao, TipoAvaliacao.PROVA, "Prova 1", semestre.inicio().plusWeeks(6), "10", alunos, true);
        avaliar(c, alocacao, TipoAvaliacao.TRABALHO, "Trabalho em grupo", semestre.inicio().plusWeeks(10), "5",
                alunos, true);
        avaliar(c, alocacao, TipoAvaliacao.PROVA, "Prova 2", semestre.fim().minusWeeks(3), "10", alunos, true);
    }

    private void avaliar(Contexto c, AlocacaoProfessor alocacao, TipoAvaliacao tipo, String titulo, LocalDate data,
            String valor, List<AlunoMock> alunos, boolean publicada) {
        Avaliacao avaliacao = criarAvaliacao(c, alocacao, tipo, titulo, data, new BigDecimal(valor), publicada);
        if (data.isBefore(c.hoje)) {
            lancarNotas(c, alocacao, avaliacao, alunos, publicada);
        }
    }

    private Avaliacao criarAvaliacao(Contexto c, AlocacaoProfessor alocacao, TipoAvaliacao tipo, String titulo,
            LocalDate data, BigDecimal valor, boolean publicada) {
        Avaliacao avaliacao = avaliacaoRepository.save(Avaliacao.builder()
                .escolaId(c.escolaId)
                .turma(alocacao.getTurma())
                .disciplina(alocacao.getDisciplina())
                .professor(alocacao.getProfessor())
                .tipo(tipo)
                .titulo(titulo)
                .data(data)
                .valor(valor)
                .notasPublicadas(publicada)
                .build());
        // Marcada de uma a tres semanas antes; a lista da semana, poucos dias antes.
        int antecedencia = tipo == TipoAvaliacao.TAREFA ? c.gerador.entre(2, 4) : c.gerador.entre(7, 20);
        Instant criada = c.em(data.minusDays(antecedencia), 13, 17);
        c.datar("AVALIACAO", "ID_AVALIACAO", avaliacao.getId(), criada);
        c.registrar(alocacao.getProfessor().getUsuarioId(), OperacaoAuditoria.CRIAR, EntidadeAuditada.AVALIACAO,
                avaliacao.getId(), criada);
        return avaliacao;
    }

    /** Notas lancadas a noite, de dois a sete dias depois da avaliacao (sem passar de ontem). */
    private void lancarNotas(Contexto c, AlocacaoProfessor alocacao, Avaliacao avaliacao, List<AlunoMock> alunos,
            boolean publicada) {
        if (alunos.isEmpty()) {
            return;
        }
        LocalDate dia = avaliacao.getData().plusDays(c.gerador.entre(2, 7));
        Instant lancadas = c.em(dia.isBefore(c.hoje) ? dia : c.hoje.minusDays(1), 19, 21);
        List<Nota> notas = notaRepository.saveAll(alunos.stream()
                .map(aluno -> Nota.builder()
                        .escolaId(c.escolaId)
                        .avaliacao(avaliacao)
                        .aluno(aluno.aluno)
                        .valor(nota(aluno.desempenho, avaliacao.getValor(), c.gerador))
                        .build())
                .toList());
        notas.forEach(nota -> c.datar("NOTA", "ID_NOTA", nota.getId(), lancadas));
        if (publicada && notas.size() == alunos.size()) {
            c.registrar(alocacao.getProfessor().getUsuarioId(), OperacaoAuditoria.PUBLICAR_NOTAS,
                    EntidadeAuditada.AVALIACAO, avaliacao.getId(), lancadas.plus(Duration.ofMinutes(4)));
        }
    }

    /** Nota de 0 a 10 em torno do desempenho do aluno, proporcional ao valor, de meio em meio ponto. */
    private static BigDecimal nota(double desempenho, BigDecimal pontos, GeradorMock gerador) {
        double deZeroADez = Math.clamp(desempenho + gerador.random().nextGaussian() * 0.8, 0, 10);
        double proporcional = deZeroADez * pontos.doubleValue() / 10;
        return BigDecimal.valueOf(Math.round(proporcional * 2) / 2.0).setScale(1, RoundingMode.UNNECESSARY);
    }

    /**
     * Uma lista de exercicios recente em cada disciplina: um terco com todas
     * as notas (metade publicada, metade em rascunho), um terco com metade e o
     * resto sem nenhuma. Sao as "notas a lancar" do professor.
     */
    private void deixarNotasPorLancar(Contexto c, List<GradeMock.Pedido<AlocacaoProfessor>> pedidos,
            Map<Long, List<AlunoMock>> alunosPorTurma, Semestre semestre) {
        BigDecimal pontos = new BigDecimal("2");
        for (int i = 0; i < pedidos.size(); i++) {
            AlocacaoProfessor alocacao = pedidos.get(i).alocacao();
            LocalDate data = c.hoje.minusDays(1 + i % 6L);
            if (data.isBefore(semestre.inicio()) || data.isAfter(semestre.fim())) {
                continue;
            }
            boolean publicada = i % 6 == 0;
            Avaliacao avaliacao = criarAvaliacao(c, alocacao, TipoAvaliacao.TAREFA, "Lista de exercícios", data,
                    pontos, publicada);
            List<AlunoMock> daTurma = alunosPorTurma.get(alocacao.getTurma().getId());
            int comNota = switch (i % 3) {
                case 0 -> daTurma.size();
                case 1 -> daTurma.size() / 2;
                default -> 0;
            };
            lancarNotas(c, alocacao, avaliacao, daTurma.subList(0, comNota), publicada);
        }
    }

    // ---- Chamadas ----

    /**
     * Chamada de toda aula da grade, do inicio do semestre ate ontem (menos
     * feriados), com uma ou outra falta. Desta semana, uma em cada cinco fica
     * sem chamada, para o professor ter o que registrar. As presencas vao por
     * JDBC em lote: sao milhares de linhas.
     */
    private void registrarChamadas(Contexto c, List<GradeMock.Aula<AlocacaoProfessor>> grade,
            Map<Long, List<AlunoMock>> alunosPorTurma, Semestre semestre) {
        LocalDate estaSemana = c.hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Aula> aulas = new ArrayList<>();
        int contador = 0;
        for (LocalDate segunda = semestre.inicio().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                segunda.isBefore(c.hoje); segunda = segunda.plusWeeks(1)) {
            for (GradeMock.Aula<AlocacaoProfessor> horario : grade) {
                LocalDate data = segunda.plusDays(horario.dia().ordinal());
                if (!data.isBefore(c.hoje) || data.isBefore(semestre.inicio()) || data.isAfter(semestre.fim())
                        || FERIADOS.contains(MonthDay.from(data))) {
                    continue;
                }
                if (!segunda.isBefore(estaSemana) && contador++ % 5 == 4) {
                    continue;
                }
                AlocacaoProfessor alocacao = horario.alocacao();
                aulas.add(Aula.builder()
                        .escolaId(c.escolaId)
                        .turma(alocacao.getTurma())
                        .disciplina(alocacao.getDisciplina())
                        .professor(alocacao.getProfessor())
                        .data(data)
                        .conteudo(CONTEUDOS.get(c.gerador.random().nextInt(CONTEUDOS.size())))
                        .build());
            }
        }
        aulaRepository.saveAll(aulas);
        entityManager.flush();

        List<Object[]> presencas = new ArrayList<>();
        for (Aula aula : aulas) {
            Instant registrada = aula.getData().atTime(horaDaChamada(aula)).atZone(ZONA).toInstant();
            c.datar("AULA", "ID_AULA", aula.getId(), registrada);
            Timestamp quando = Timestamp.from(c.passou(registrada) ? registrada : c.agora);
            for (AlunoMock aluno : alunosPorTurma.get(aula.getTurma().getId())) {
                boolean falta = c.gerador.chance(aluno.desempenho < 6 ? 0.12 : 0.05);
                presencas.add(new Object[] { c.escolaId, aula.getId(), aluno.aluno.getId(), !falta, quando });
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO FREQUENCIA (ESCOLA_ID, AULA_ID, ALUNO_ID, PRESENTE, CRIADO_EM) VALUES (?, ?, ?, ?, ?)",
                presencas);
    }

    private static java.time.LocalTime horaDaChamada(Aula aula) {
        return aula.getTurma().getTurno() == Turno.VESPERTINO ? java.time.LocalTime.of(13, 40)
                : java.time.LocalTime.of(7, 40);
    }

    // ---- Financeiro ----

    /** Cobranca montada, com o dia em que foi lancada. */
    private record CobrancaMock(Cobranca cobranca, Instant lancadaEm) {
    }

    /**
     * Mensalidades de cada mes (do mes de 12 meses atras ate o fim do
     * semestre atual), taxa de matricula de quem entrou, material didatico de
     * cada semestre, simulados do ensino medio e o passeio do 6º ano.
     *
     * <p>
     * Toda cobranca e lancada antes de vencer e nunca paga antes de lancada.
     * A maioria paga ate o vencimento; alguns atrasam uns dias; quatro
     * familias estao devendo os ultimos meses e uma ou outra cobranca ficou
     * para tras: essas estao EM_ATRASO.
     * </p>
     */
    private List<Cobranca> lancarCobrancas(Contexto c, List<AlunoMock> alunos) {
        Set<AlunoMock> inadimplentes = new LinkedHashSet<>();
        List<AlunoMock> veteranos = alunos.stream().filter(a -> a.entrada == 0).toList();
        while (inadimplentes.size() < 4) {
            inadimplentes.add(veteranos.get(c.gerador.random().nextInt(veteranos.size())));
        }

        List<CobrancaMock> cobrancas = new ArrayList<>();
        for (AlunoMock aluno : alunos) {
            for (int k = aluno.entrada; k < c.semestres.size(); k++) {
                Semestre semestre = c.semestres.get(k);
                MatriculaTurma matricula = aluno.matriculas[k];
                LocalDate matriculaEm = matricula.getDataMatricula();
                int serie = serieEm(aluno.turma, semestre, c.anoAtual());
                if (k == aluno.entrada) {
                    LocalDate vencimento = matriculaEm.plusDays(7);
                    adicionar(c, cobrancas, aluno, matricula, TipoCobranca.MATRICULA,
                            "Taxa de matrícula %d".formatted(semestre.ano()), new BigDecimal("350.00"), vencimento,
                            matriculaEm, false);
                }
                LocalDate material = semestre.inicio().withDayOfMonth(20);
                adicionar(c, cobrancas, aluno, matricula, TipoCobranca.MATERIAL,
                        "Material didático - " + semestre.nome(), new BigDecimal("420.00"), material,
                        maior(material.minusDays(25), matriculaEm.plusDays(1)), false);
                BigDecimal valor = mensalidade(serie, semestre.ano() < c.anoAtual());
                for (int mes : semestre.meses()) {
                    LocalDate vencimento = LocalDate.of(semestre.ano(), mes, DIA_VENCIMENTO);
                    String descricao = "Mensalidade %s/%d".formatted(
                            vencimento.getMonth().getDisplayName(TextStyle.FULL, PT_BR), semestre.ano());
                    boolean devendo = inadimplentes.contains(aluno)
                            && !vencimento.isBefore(c.hoje.minusMonths(3));
                    adicionar(c, cobrancas, aluno, matricula, TipoCobranca.MENSALIDADE, descricao, valor, vencimento,
                            maior(vencimento.minusDays(c.gerador.entre(15, 25)), matriculaEm.plusDays(1)),
                            devendo || c.gerador.chance(0.025));
                }
                if (medio(serie) && semestre.primeiro()) {
                    LocalDate vencimento = LocalDate.of(semestre.ano(), 4, 15);
                    adicionar(c, cobrancas, aluno, matricula, TipoCobranca.OUTRO,
                            "Simulados ENEM %d".formatted(semestre.ano()), new BigDecimal("120.00"), vencimento,
                            vencimento.minusDays(30), c.gerador.chance(0.05));
                }
            }
        }

        // Passeio do 6º ano no semestre atual; duas familias desistiram antes de vencer.
        List<AlunoMock> sextoAno = alunosDaTurma(alunos, TURMAS.getFirst(), c.atual());
        LocalDate passeio = c.semestres.get(c.atual()).inicio().plusDays(52);
        for (int i = 0; i < sextoAno.size(); i++) {
            AlunoMock aluno = sextoAno.get(i);
            int antes = cobrancas.size();
            adicionar(c, cobrancas, aluno, aluno.matriculas[c.atual()], TipoCobranca.OUTRO,
                    "Passeio pedagógico - Museu de Ciências Naturais", new BigDecimal("95.00"), passeio,
                    passeio.minusDays(21), i < 2);
            if (i < 2 && cobrancas.size() > antes) {
                cancelar(c, cobrancas.getLast());
            }
        }

        escolherFormaDasPagas(cobrancas);
        List<Cobranca> entidades = cobrancaRepository.saveAll(cobrancas.stream().map(CobrancaMock::cobranca).toList());
        // O provedor avisa do pagamento um ou dois minutos depois.
        Map<UUID, Instant> pagasEm = entidades.stream().filter(cobranca -> cobranca.getPagaEm() != null)
                .collect(Collectors.toMap(Cobranca::getId, Cobranca::getPagaEm));
        pagamentoRepository.saveAll(pagamentosOnline(c, entidades)).forEach(pagamento -> c.datar(
                "PAGAMENTO_COBRANCA", "ID_PAGAMENTO", "REGISTRADO_EM", pagamento.getId(),
                pagasEm.get(pagamento.getCobrancaId()).plus(Duration.ofMinutes(2))));
        for (CobrancaMock mock : cobrancas) {
            auditarCobranca(c, mock);
        }
        return entidades;
    }

    private static LocalDate maior(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    /**
     * Monta a cobranca se ela vence dentro da janela (do mes de 12 meses atras
     * em diante) e decide se foi paga: o pagamento cai depois do lancamento e
     * antes de agora; o que nao foi pago e ja venceu fica EM_ATRASO.
     */
    private void adicionar(Contexto c, List<CobrancaMock> cobrancas, AlunoMock aluno, MatriculaTurma matricula,
            TipoCobranca tipo, String descricao, BigDecimal valor, LocalDate vencimento, LocalDate lancamento,
            boolean naoPaga) {
        if (vencimento.isBefore(c.janela)) {
            return;
        }
        Instant lancadaEm = c.em(lancamento.isBefore(vencimento) ? lancamento : vencimento.minusDays(1), 8, 11);
        Cobranca cobranca = Cobranca.builder()
                .escolaId(c.escolaId)
                .aluno(aluno.aluno)
                .matricula(matricula)
                .responsavelId(aluno.financeiroId)
                .descricao(descricao)
                .tipo(tipo)
                .valor(valor)
                .dataVencimento(vencimento)
                .status(StatusCobranca.PENDENTE)
                .build();
        cobrancas.add(new CobrancaMock(cobranca, lancadaEm));

        Instant pagaEm = naoPaga ? null : momentoDoPagamento(c, vencimento, lancadaEm);
        if (c.passou(pagaEm)) {
            cobranca.setStatus(StatusCobranca.PAGA);
            cobranca.setPagaEm(pagaEm);
            cobranca.setAtualizadoEm(pagaEm);
        } else if (vencimento.isBefore(c.hoje)) {
            // A rotina da madrugada marca o atraso no dia seguinte ao vencimento.
            cobranca.setStatus(StatusCobranca.EM_ATRASO);
            cobranca.setAtualizadoEm(vencimento.plusDays(1).atTime(0, 30).atZone(ZONA).toInstant());
        }
    }

    /** A maioria paga na semana do vencimento; alguns adiantados, outros atrasados. */
    private static Instant momentoDoPagamento(Contexto c, LocalDate vencimento, Instant lancadaEm) {
        double perfil = c.gerador.random().nextDouble();
        LocalDate dia = perfil < 0.70 ? vencimento.minusDays(c.gerador.entre(0, 8))
                : perfil < 0.85 ? vencimento.plusDays(c.gerador.entre(1, 15))
                        : vencimento.minusDays(c.gerador.entre(9, 14));
        Instant pagaEm = c.em(dia, 7, 21);
        if (!pagaEm.isAfter(lancadaEm)) {
            pagaEm = lancadaEm.plus(Duration.ofHours(c.gerador.entre(20, 70)));
        }
        return pagaEm;
    }

    /** Cancela uma cobranca em aberto, dias depois de lancada (se esse dia ja passou). */
    private static void cancelar(Contexto c, CobrancaMock mock) {
        Cobranca cobranca = mock.cobranca();
        Instant canceladaEm = mock.lancadaEm().plus(Duration.ofDays(c.gerador.entre(2, 6)));
        if (cobranca.getStatus() == StatusCobranca.PAGA || !c.passou(canceladaEm)) {
            return;
        }
        cobranca.setStatus(StatusCobranca.CANCELADA);
        cobranca.setCanceladaEm(canceladaEm);
        cobranca.setAtualizadoEm(canceladaEm);
    }

    /**
     * De cada dez pagas, seis foram por PIX, tres por boleto e uma teve baixa
     * manual, pela posicao, sem sortear.
     *
     * <p>
     * A cobranca fica NAO_EMITIDA de proposito: ela nao existe no provedor, e
     * assim nada tenta busca-la la. O comprovante sai so do pagamento, e o
     * boleto responde BOLETO_NAO_GERADO.
     * </p>
     */
    private static void escolherFormaDasPagas(List<CobrancaMock> cobrancas) {
        List<Cobranca> pagas = cobrancas.stream().map(CobrancaMock::cobranca)
                .filter(c -> c.getStatus() == StatusCobranca.PAGA).toList();
        for (int i = 0; i < pagas.size(); i++) {
            int posicao = i % 10;
            if (posicao < 9) {
                pagas.get(i).setFormaPagamento(posicao < 6 ? FormaPagamento.PIX : FormaPagamento.BOLETO);
            }
        }
    }

    /** Pagamento de cada cobranca paga online, com a taxa do provedor e o dia do credito. */
    private static List<PagamentoCobranca> pagamentosOnline(Contexto c, List<Cobranca> cobrancas) {
        return cobrancas.stream()
                .filter(cobranca -> cobranca.getStatus() == StatusCobranca.PAGA
                        && cobranca.getFormaPagamento() != null)
                .map(cobranca -> {
                    boolean pix = cobranca.getFormaPagamento() == FormaPagamento.PIX;
                    LocalDate pagoEm = LocalDate.ofInstant(cobranca.getPagaEm(), ZONA);
                    return PagamentoCobranca.builder()
                            .escolaId(c.escolaId)
                            .cobrancaId(cobranca.getId())
                            .valorPago(cobranca.getValor())
                            .valorLiquido(cobranca.getValor().subtract(new BigDecimal(pix ? "1.99" : "3.49")))
                            .dataPagamento(pagoEm)
                            .dataCredito(pix ? pagoEm : pagoEm.plusDays(1))
                            .formaPagamento(cobranca.getFormaPagamento())
                            .origem(OrigemPagamento.ONLINE)
                            .idTransacaoExterna("mock_" + cobranca.getId())
                            .build();
                })
                .toList();
    }

    private static void auditarCobranca(Contexto c, CobrancaMock mock) {
        Cobranca cobranca = mock.cobranca();
        UUID financeiro = c.equipe.financeiro();
        c.datar("COBRANCA", "ID_COBRANCA", cobranca.getId(), mock.lancadaEm());
        c.registrar(financeiro, OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, cobranca.getId(),
                mock.lancadaEm());
        if (cobranca.getStatus() == StatusCobranca.PAGA) {
            if (cobranca.getFormaPagamento() == null) {
                c.registrar(financeiro, OperacaoAuditoria.CONFIRMAR_PAGAMENTO, EntidadeAuditada.COBRANCA,
                        cobranca.getId(), cobranca.getPagaEm());
            } else {
                c.registrar(OrigemAuditoria.PROVEDOR, null, OperacaoAuditoria.PAGAR, EntidadeAuditada.COBRANCA,
                        cobranca.getId(), cobranca.getPagaEm().plus(Duration.ofMinutes(2)));
            }
        } else if (cobranca.getStatus() == StatusCobranca.CANCELADA) {
            c.registrar(financeiro, OperacaoAuditoria.CANCELAR, EntidadeAuditada.COBRANCA, cobranca.getId(),
                    cobranca.getCanceladaEm());
        }
    }

    private record DespesaMock(String categoria, String descricao, String valor, int dia, boolean cancelada) {
    }

    /**
     * Quanto a escola cobra de mensalidade num mes tipico de cada ano: a soma
     * das mensalidades do ano dividida pelos meses que ele tem na janela.
     */
    private static Map<Integer, BigDecimal> mensalidadesPorMes(List<Cobranca> cobrancas) {
        Map<Integer, BigDecimal> soma = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> meses = new LinkedHashMap<>();
        for (Cobranca cobranca : cobrancas) {
            if (cobranca.getTipo() == TipoCobranca.MENSALIDADE) {
                int ano = cobranca.getDataVencimento().getYear();
                soma.merge(ano, cobranca.getValor(), BigDecimal::add);
                meses.computeIfAbsent(ano, chave -> new LinkedHashSet<>())
                        .add(cobranca.getDataVencimento().getMonthValue());
            }
        }
        soma.replaceAll((ano, total) -> total.divide(BigDecimal.valueOf(meses.get(ano).size()), 2,
                RoundingMode.HALF_UP));
        return soma;
    }

    /**
     * Contas fixas de todo mes e as despesas de cada epoca do ano (13º em
     * novembro e dezembro, festa junina, pintura nas ferias...), da janela de
     * 12 meses ate o fim do semestre atual.
     *
     * <p>
     * Os valores sao uma fracao da mensalidade de um mes tipico do ano, para a
     * escola fechar todo mes no azul: a folha fica em 38%, o aluguel em 9%, e
     * o mes mais caro (dezembro, com a 2ª parcela do 13º) gasta perto de 75%
     * do que entra.
     * </p>
     */
    private void lancarDespesas(Contexto c, Map<Integer, BigDecimal> mensalidadesPorMes) {
        categoriaDespesaService.listar();
        Map<String, CategoriaDespesa> categorias = categoriaDespesaRepository.findAllByEscolaIdOrderByNome(c.escolaId)
                .stream().collect(Collectors.toMap(CategoriaDespesa::getNome, Function.identity()));

        List<Despesa> despesas = new ArrayList<>();
        List<Instant> lancadas = new ArrayList<>();
        YearMonth ultimo = YearMonth.from(c.semestres.get(c.atual()).fim());
        for (YearMonth mes = YearMonth.from(c.janela); !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            BigDecimal receita = mensalidadesPorMes.getOrDefault(mes.getYear(),
                    mensalidadesPorMes.values().iterator().next());
            Function<String, String> parte = fracao -> receita.multiply(new BigDecimal(fracao))
                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
            String nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR) + "/" + mes.getYear();
            List<DespesaMock> doMes = new ArrayList<>(List.of(
                    new DespesaMock("Salários", "Folha de pagamento - " + nomeMes, parte.apply("0.38"), 5, false),
                    new DespesaMock("Contas", "Aluguel do prédio - " + nomeMes, parte.apply("0.09"), 10, false),
                    new DespesaMock("Contas", "Energia elétrica - " + nomeMes,
                            parte.apply("0.0" + c.gerador.entre(20, 28)), 15, false),
                    new DespesaMock("Contas", "Água e esgoto - " + nomeMes,
                            parte.apply("0.00" + c.gerador.entre(70, 90)), 18, false),
                    new DespesaMock("Contas", "Internet e telefonia - " + nomeMes, "489.90", 20, false),
                    new DespesaMock("Outros", "Honorários do contador - " + nomeMes, "1100.00", 12, false)));
            doMes.addAll(switch (mes.getMonthValue()) {
                case 1 -> List.of(new DespesaMock("Consertos", "Pintura das salas nas férias", parte.apply("0.08"),
                        22, false));
                case 2 -> List.of(
                        new DespesaMock("Materiais", "Livros para a biblioteca", parte.apply("0.04"), 12, false),
                        new DespesaMock("Materiais", "Material de papelaria do semestre", parte.apply("0.012"), 14,
                                false));
                case 3 -> List.of(new DespesaMock("Consertos", "Manutenção dos aparelhos de ar-condicionado",
                        parte.apply("0.025"), 22, false));
                case 4 -> List.of(new DespesaMock("Limpeza", "Produtos de limpeza e descartáveis",
                        parte.apply("0.015"), 8, false));
                case 5 -> List.of(
                        new DespesaMock("Consertos", "Reparo no telhado da quadra", parte.apply("0.06"), 25, true),
                        new DespesaMock("Consertos", "Reparo no telhado da quadra (novo orçamento)",
                                parte.apply("0.05"), 28, false));
                case 6 -> List.of(new DespesaMock("Outros", "Festa junina", parte.apply("0.03"), 12, false));
                case 7 -> List.of(new DespesaMock("Limpeza", "Dedetização do prédio", parte.apply("0.01"), 20,
                        false));
                case 8 -> List.of(new DespesaMock("Materiais", "Material de papelaria do semestre",
                        parte.apply("0.012"), 14, false));
                case 9 -> List.of(new DespesaMock("Limpeza", "Produtos de limpeza e descartáveis",
                        parte.apply("0.015"), 8, false));
                case 10 -> List.of(new DespesaMock("Outros", "Semana da criança", parte.apply("0.02"), 9, false));
                case 11 -> List.of(new DespesaMock("Salários", "13º salário - 1ª parcela", parte.apply("0.19"), 30,
                        false));
                case 12 -> List.of(
                        new DespesaMock("Salários", "13º salário - 2ª parcela", parte.apply("0.19"), 18, false),
                        new DespesaMock("Outros", "Confraternização de fim de ano", parte.apply("0.03"), 12, false));
                default -> List.<DespesaMock>of();
            });
            for (DespesaMock mock : doMes) {
                LocalDate vencimento = mes.atDay(Math.min(mock.dia(), mes.lengthOfMonth()));
                Instant lancadaEm = c.em(vencimento.minusDays(c.gerador.entre(5, 15)), 9, 11);
                despesas.add(despesa(c, categorias.get(mock.categoria()), mock, vencimento, lancadaEm));
                lancadas.add(lancadaEm);
            }
        }
        despesaRepository.saveAll(despesas);
        UUID financeiro = c.equipe.financeiro();
        for (int i = 0; i < despesas.size(); i++) {
            Despesa despesa = despesas.get(i);
            c.datar("DESPESA", "ID_DESPESA", despesa.getId(), lancadas.get(i));
            c.registrar(financeiro, OperacaoAuditoria.CRIAR, EntidadeAuditada.DESPESA, despesa.getId(),
                    lancadas.get(i));
            if (despesa.getPagaEm() != null) {
                c.registrar(financeiro, OperacaoAuditoria.PAGAR, EntidadeAuditada.DESPESA, despesa.getId(),
                        despesa.getPagaEm());
            }
            if (despesa.getCanceladaEm() != null) {
                c.registrar(financeiro, OperacaoAuditoria.CANCELAR, EntidadeAuditada.DESPESA, despesa.getId(),
                        despesa.getCanceladaEm());
            }
        }
    }

    /** Vencida esta paga na vespera, menos a conta de luz do ultimo mes, que ficou para tras. */
    private static Despesa despesa(Contexto c, CategoriaDespesa categoria, DespesaMock mock, LocalDate vencimento,
            Instant lancadaEm) {
        Despesa despesa = Despesa.builder()
                .escolaId(c.escolaId)
                .categoria(categoria)
                .descricao(mock.descricao())
                .valor(new BigDecimal(mock.valor()))
                .dataVencimento(vencimento)
                .situacao(SituacaoDespesa.A_PAGAR)
                .build();
        boolean luzAtrasada = mock.descricao().startsWith("Energia") && !vencimento.isBefore(c.hoje.minusMonths(1));
        Instant canceladaEm = lancadaEm.plus(Duration.ofDays(3));
        Instant pagaEm = maior(c.em(vencimento.minusDays(1), 9, 16), lancadaEm.plus(Duration.ofHours(2)));
        if (mock.cancelada() && c.passou(canceladaEm)) {
            despesa.setSituacao(SituacaoDespesa.CANCELADA);
            despesa.setCanceladaEm(canceladaEm);
            despesa.setAtualizadoEm(canceladaEm);
        } else if (!mock.cancelada() && vencimento.isBefore(c.hoje) && !luzAtrasada && c.passou(pagaEm)) {
            despesa.setSituacao(SituacaoDespesa.PAGA);
            despesa.setPagaEm(pagaEm);
            despesa.setAtualizadoEm(pagaEm);
        }
        return despesa;
    }

    private static Instant maior(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    /**
     * Faturas do Eduka (plano Enterprise) de cada mes da janela: lancadas pelo
     * Super Admin no dia 1º e vencendo no dia 15. As vencidas estao pagas, e a
     * do mes, se ainda nao venceu, esta em aberto: nada em atraso, para a
     * rotina de suspensao nao bloquear a escola de demonstracao.
     */
    private void lancarFaturas(Contexto c) {
        UUID superAdmin = c.equipe.superAdmin();
        int posicao = 0;
        for (YearMonth mes = YearMonth.from(c.janela); !mes.isAfter(YearMonth.from(c.hoje)); mes = mes.plusMonths(1)) {
            LocalDate competencia = mes.atDay(1);
            LocalDate vencimento = mes.atDay(15);
            Instant lancadaEm = c.em(competencia, 9, 10);
            FaturaPlataforma fatura = FaturaPlataforma.builder()
                    .escolaId(c.escolaId)
                    .competencia(competencia)
                    .descricao("Assinatura Edúka Enterprise - %s/%d".formatted(
                            mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR), mes.getYear()))
                    .valor(new BigDecimal(mes.getYear() < c.anoAtual() ? "1190.00" : "1290.00"))
                    .dataVencimento(vencimento)
                    .criadaPor(superAdmin)
                    .build();
            Instant pagaEm = maior(c.em(vencimento.minusDays(c.gerador.entre(0, 5)), 9, 18),
                    lancadaEm.plus(Duration.ofHours(30)));
            if (vencimento.isBefore(c.hoje) && c.passou(pagaEm)) {
                fatura.setStatus(StatusCobranca.PAGA);
                fatura.setFormaPagamento(posicao++ % 3 == 2 ? FormaPagamento.BOLETO : FormaPagamento.PIX);
                fatura.setValorPago(fatura.getValor());
                fatura.setDataPagamento(LocalDate.ofInstant(pagaEm, ZONA));
                fatura.setPagaEm(pagaEm);
                fatura.setAtualizadoEm(pagaEm);
                fatura.setIdTransacaoExterna("mock_fatura_" + competencia);
            }
            fatura = faturaRepository.save(fatura);
            c.datar("FATURA_PLATAFORMA", "ID_FATURA", fatura.getId(), lancadaEm);
            c.registrar(superAdmin, OperacaoAuditoria.CRIAR, EntidadeAuditada.FATURA_PLATAFORMA, fatura.getId(),
                    lancadaEm);
            if (fatura.getPagaEm() != null) {
                c.registrar(OrigemAuditoria.PROVEDOR, null, OperacaoAuditoria.PAGAR,
                        EntidadeAuditada.FATURA_PLATAFORMA, fatura.getId(), pagaEm.plus(Duration.ofMinutes(2)));
            }
        }
    }

    // ---- Solicitacoes, aceites e datas ----

    private record SolicitacaoMock(String mensagem, int diasAtras, boolean atendida) {
    }

    private static final List<SolicitacaoMock> SOLICITACOES = List.of(
            new SolicitacaoMock("Meu sobrenome está sem o acento no cadastro.", 150, true),
            new SolicitacaoMock("Mudamos de endereço: agora é Rua Pouso Alegre, 412, Floresta, CEP 31015-030.",
                    96, true),
            new SolicitacaoMock("O celular da minha mãe mudou para (31) 98765-1122.", 61, true),
            new SolicitacaoMock("Minha data de nascimento está com o dia trocado.", 24, true),
            new SolicitacaoMock("O e-mail do meu pai no cadastro é antigo, ele não usa mais.", 4, false),
            new SolicitacaoMock("Gostaria de incluir minha avó como responsável para buscar na escola.", 1, false));

    /** Pedidos de correcao de cadastro feitos por alunos; os antigos ja foram atendidos pela secretaria. */
    private void abrirSolicitacoes(Contexto c, List<AlunoMock> alunos) {
        List<AlunoMock> veteranos = alunos.stream().filter(a -> a.entrada == 0).toList();
        for (int i = 0; i < SOLICITACOES.size(); i++) {
            SolicitacaoMock mock = SOLICITACOES.get(i);
            AlunoMock aluno = veteranos.get((i * 7) % veteranos.size());
            Instant criada = c.em(c.hoje.minusDays(mock.diasAtras()), 18, 21);
            Instant atendida = mock.atendida() ? c.em(c.hoje.minusDays(mock.diasAtras() - 2L), 9, 11) : null;
            SolicitacaoCorrecao solicitacao = solicitacaoRepository.save(SolicitacaoCorrecao.builder()
                    .escolaId(c.escolaId)
                    .alunoId(aluno.aluno.getId())
                    .mensagem(mock.mensagem())
                    .status(mock.atendida() ? StatusSolicitacaoCorrecao.ATENDIDA : StatusSolicitacaoCorrecao.ABERTA)
                    .atendidaEm(atendida)
                    .build());
            c.datar("SOLICITACAO_CORRECAO", "ID_SOLICITACAO_CORRECAO", solicitacao.getId(), criada);
            if (mock.atendida()) {
                c.registrar(c.equipe.secretaria(), OperacaoAuditoria.EDITAR, EntidadeAuditada.ALUNO,
                        aluno.aluno.getId(), atendida.minus(Duration.ofMinutes(3)));
            }
        }
    }

    /**
     * Equipe e professores ja aceitaram o aviso de privacidade em vigor; os
     * alunos nao, para o aviso aparecer no primeiro acesso deles.
     */
    private void aceitarAvisoDePrivacidade(Contexto c, java.util.Collection<Professor> professores) {
        Instant publicado = AvisoPrivacidade.PUBLICADO_EM.atStartOfDay(ZONA).toInstant();
        List<UUID> usuarios = new ArrayList<>(c.equipe.usuarios().stream().map(Usuario::getId).toList());
        professores.stream().map(Professor::getUsuarioId).distinct().forEach(usuarios::add);
        List<AceitePrivacidade> aceites = new ArrayList<>();
        for (UUID usuario : usuarios) {
            Instant aceitoEm = maior(publicado, c.agora.minus(Duration.ofMinutes(c.gerador.entre(30, 600))));
            if (c.passou(aceitoEm)) {
                aceites.add(AceitePrivacidade.builder()
                        .usuarioId(usuario).versao(AvisoPrivacidade.VERSAO).aceitoEm(aceitoEm).build());
            }
        }
        aceiteRepository.saveAll(aceites);
    }

    /**
     * Grava as datas de criacao retroativas. Os inserts do JPA vao antes (o
     * flush): o UPDATE precisa das linhas no banco.
     */
    private void gravarDatas(Contexto c) {
        entityManager.flush();
        c.datas.forEach((sql, linhas) -> jdbcTemplate.batchUpdate(sql, linhas));
    }

    // ---- Apoio ----

    /** Roda {@code acao} com a escola do usuario no SecurityContext, e depois restaura o anterior. */
    private static void comoEscola(Usuario usuario, Runnable acao) {
        SecurityContext anterior = SecurityContextHolder.getContext();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        UsuarioAutenticado principal = new UsuarioAutenticado(usuario.getId(), usuario.getPerfil().name(),
                usuario.getEscolaId());
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        SecurityContextHolder.setContext(contexto);
        try {
            acao.run();
        } finally {
            SecurityContextHolder.setContext(anterior);
        }
    }
}
