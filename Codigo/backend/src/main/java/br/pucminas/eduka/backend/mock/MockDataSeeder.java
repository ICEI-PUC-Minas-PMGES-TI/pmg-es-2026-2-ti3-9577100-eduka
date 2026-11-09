package br.pucminas.eduka.backend.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import br.pucminas.eduka.backend.domain.Endereco;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.Frequencia;
import br.pucminas.eduka.backend.domain.HorarioTurma;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.ModuloEscola;
import br.pucminas.eduka.backend.domain.Nota;
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
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.TipoAvaliacao;
import br.pucminas.eduka.backend.domain.TipoCobranca;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.domain.Turno;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.domain.VinculoProfessor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
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
import br.pucminas.eduka.backend.repository.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.HorarioTurmaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.NotaRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.ResponsavelRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.CategoriaDespesaService;

/**
 * Escola de demonstracao com cara de escola de verdade: o "Colegio Ipe
 * Amarelo", com direcao, secretaria, financeiro, 8 professores, 5 turmas e 50
 * alunos com responsaveis, notas, mensalidades e despesas do semestre atual,
 * mais a grade semanal das turmas, as chamadas desta semana e algumas notas
 * ainda por lancar.
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
 * Todas as pessoas sao ficticias ({@link GeradorMock}); todos os logins
 * terminam em {@value GeradorMock#DOMINIO} e entram com {@code app.mock.senha}
 * ({@value #SENHA} em dev; no demo, a senha de {@code MOCK_SENHA}). As
 * datas sao relativas ao dia em que o banco e semeado: o semestre e o atual,
 * o que ja venceu esta pago (com alguns atrasos) e o resto esta em aberto.
 * Semeia uma vez so, enquanto a escola nao existe; para recriar, apague o
 * volume do Postgres ({@code docker compose down -v}).
 * </p>
 */
@Component
@Profile("(dev & !prod) | demo")
@ConditionalOnProperty(name = "app.mock.habilitado", havingValue = "true")
@Order(3)
public class MockDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MockDataSeeder.class);

    static final String CNPJ = "34567891000184";
    static final String SENHA = "Eduka-mock123";

    private static final String NOME_ESCOLA = "Colégio Ipê Amarelo";
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final long SEMENTE = 2026L;
    private static final int ALUNOS_POR_TURMA = 10;
    private static final int DIA_VENCIMENTO = 10;
    /** Horas de carga por aula semanal: 160 h viram 4 aulas por semana, 80 h viram 2. */
    private static final int HORAS_POR_AULA_SEMANAL = 40;
    private static final List<String> CONTEUDOS = List.of(
            "Exposição do conteúdo novo e exercícios em sala",
            "Correção da lista de exercícios e dúvidas",
            "Atividade em grupo e discussão dos resultados",
            "Revisão do capítulo e exercícios do livro");

    private record Funcionario(String nome, PerfilUsuario perfil) {
    }

    private static final List<Funcionario> EQUIPE = List.of(
            new Funcionario("Helena Martins Duarte", PerfilUsuario.MASTER),
            new Funcionario("Patrícia Lopes Andrade", PerfilUsuario.SECRETARIA),
            new Funcionario("Rodrigo Nunes Ferreira", PerfilUsuario.SECRETARIA),
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

    private record TurmaMock(String nome, String codigo, boolean medio, Turno turno, int idade,
            BigDecimal mensalidade) {
    }

    private static final List<TurmaMock> TURMAS = List.of(
            new TurmaMock("6º ano A", "EF2-6A", false, Turno.MATUTINO, 11, new BigDecimal("1450.00")),
            new TurmaMock("8º ano A", "EF2-8A", false, Turno.MATUTINO, 13, new BigDecimal("1450.00")),
            new TurmaMock("9º ano B", "EF2-9B", false, Turno.VESPERTINO, 14, new BigDecimal("1450.00")),
            new TurmaMock("1º ano A", "EM-1A", true, Turno.MATUTINO, 15, new BigDecimal("1680.00")),
            new TurmaMock("3º ano A", "EM-3A", true, Turno.MATUTINO, 17, new BigDecimal("1680.00")));

    private record Semestre(String nome, LocalDate inicio, LocalDate fim) {
    }

    /** Pais de um ou mais alunos: irmaos dividem endereco e responsaveis. */
    private static final class Familia {
        private final String sobrenomeMae;
        private final String sobrenomePai;
        private final String[] endereco;
        private final Map<Pessoa, String> responsaveis = new LinkedHashMap<>();
        private Pessoa financeiro;
        private boolean temIrmao;

        private Familia(String sobrenomeMae, String sobrenomePai, String[] endereco) {
            this.sobrenomeMae = sobrenomeMae;
            this.sobrenomePai = sobrenomePai;
            this.endereco = endereco;
        }
    }

    private record AlunoMock(Aluno aluno, MatriculaTurma matricula, TurmaMock turma, UUID financeiroId,
            double desempenho) {
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
    private final FrequenciaRepository frequenciaRepository;
    private final CobrancaRepository cobrancaRepository;
    private final PagamentoCobrancaRepository pagamentoRepository;
    private final CategoriaDespesaService categoriaDespesaService;
    private final CategoriaDespesaRepository categoriaDespesaRepository;
    private final DespesaRepository despesaRepository;
    private final PasswordEncoder passwordEncoder;
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
            FrequenciaRepository frequenciaRepository, CobrancaRepository cobrancaRepository, PagamentoCobrancaRepository pagamentoRepository,
            CategoriaDespesaService categoriaDespesaService,
            CategoriaDespesaRepository categoriaDespesaRepository, DespesaRepository despesaRepository,
            PasswordEncoder passwordEncoder, Clock clock, PlatformTransactionManager transactionManager,
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
        this.frequenciaRepository = frequenciaRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.categoriaDespesaService = categoriaDespesaService;
        this.categoriaDespesaRepository = categoriaDespesaRepository;
        this.despesaRepository = despesaRepository;
        this.passwordEncoder = passwordEncoder;
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
        LocalDate hoje = LocalDate.now(clock.withZone(ZONA));
        GeradorMock gerador = new GeradorMock(SEMENTE);
        String senhaHash = passwordEncoder.encode(senha);

        Usuario master = transactionTemplate.execute(status -> criarEscolaEEquipe(hoje, gerador, senhaHash));
        comoEscola(master, () -> transactionTemplate
                .executeWithoutResult(status -> semear(master.getEscolaId(), hoje, gerador, senhaHash)));

        // A senha padrao de dev esta no README; uma senha propria (demo) nunca vai para o log.
        // Sem o login da diretora: login e dado pessoal, mesmo no mock. Os logins estao no README.
        log.info("Mock: {} semeado. Logins em @{} com a senha {}", NOME_ESCOLA,
                GeradorMock.DOMINIO, SENHA.equals(senha) ? SENHA : "de MOCK_SENHA");
    }

    private Usuario criarEscolaEEquipe(LocalDate hoje, GeradorMock gerador, String senhaHash) {
        Escola escola = Escola.cadastrar(NOME_ESCOLA, CNPJ,
                new Endereco("Rua dos Timbiras", "1560", "Funcionários", "Belo Horizonte", "MG", "30140061"),
                "secretaria@" + GeradorMock.DOMINIO, "(31) 3261-4500", PlanoEscola.ENTERPRISE, true);
        escola.atualizarAssinatura(hoje.plusYears(2), 500, EnumSet.allOf(ModuloEscola.class));
        UUID escolaId = escolaRepository.save(escola).getId();

        Usuario master = null;
        for (Funcionario funcionario : EQUIPE) {
            Pessoa pessoa = criarAdulto(escolaId, funcionario.nome(), gerador, PapelPessoa.FUNCIONARIO);
            Usuario usuario = criarLogin(pessoa, funcionario.perfil(), senhaHash);
            if (funcionario.perfil() == PerfilUsuario.MASTER) {
                master = usuario;
            }
        }
        return master;
    }

    private void semear(UUID escolaId, LocalDate hoje, GeradorMock gerador, String senhaHash) {
        Escola escola = escolaRepository.getReferenceById(escolaId);
        Semestre semestre = semestreDe(hoje);
        PeriodoLetivo periodo = periodoLetivoRepository.save(PeriodoLetivo.builder()
                .escola(escola).nome(semestre.nome()).dataInicio(semestre.inicio()).dataFim(semestre.fim())
                .build());
        Curso fundamental = cursoRepository.save(Curso.builder()
                .escola(escola).nome("Ensino Fundamental II").codigo("EF2").build());
        Curso medio = cursoRepository.save(Curso.builder().escola(escola).nome("Ensino Médio").codigo("EM").build());

        Map<String, Disciplina> disciplinas = new LinkedHashMap<>();
        DISCIPLINAS.forEach((codigo, nomeECarga) -> disciplinas.put(codigo, disciplinaRepository.save(
                Disciplina.builder().escola(escola).nome(nomeECarga.getKey()).codigo(codigo)
                        .cargaHoraria(nomeECarga.getValue()).build())));

        Map<String, Professor> professorPorDisciplina = new LinkedHashMap<>();
        for (ProfessorMock mock : PROFESSORES) {
            Professor professor = criarProfessor(escolaId, mock, gerador, senhaHash);
            mock.disciplinas().forEach(codigo -> professorPorDisciplina.put(codigo, professor));
        }

        List<Familia> familias = new ArrayList<>();
        List<AlunoMock> alunos = new ArrayList<>();
        List<GradeMock.Pedido<AlocacaoProfessor>> pedidos = new ArrayList<>();
        Map<Long, List<AlunoMock>> alunosPorTurma = new LinkedHashMap<>();
        // Gerador proprio para o que veio depois (grade, chamadas, notas pendentes): o principal
        // segue a mesma sequencia de antes, e as pessoas e notas ja semeadas nao mudam.
        GeradorMock extra = new GeradorMock(SEMENTE + 1);
        int sequencia = 1;
        for (TurmaMock turmaMock : TURMAS) {
            List<String> grade = turmaMock.medio() ? GRADE_MEDIO : GRADE_FUNDAMENTAL;
            Turma turma = criarTurma(escola, periodo, turmaMock.medio() ? medio : fundamental, turmaMock,
                    grade.stream().map(disciplinas::get).toList());
            List<AlocacaoProfessor> alocacoes = grade.stream()
                    .map(codigo -> alocacaoRepository.save(AlocacaoProfessor.builder()
                            .escolaId(escolaId).professor(professorPorDisciplina.get(codigo)).turma(turma)
                            .disciplina(disciplinas.get(codigo)).build()))
                    .toList();

            List<AlunoMock> daTurma = new ArrayList<>();
            for (int i = 0; i < ALUNOS_POR_TURMA; i++) {
                Familia familia = familiaDoProximoAluno(escolaId, familias, gerador);
                daTurma.add(criarAluno(escolaId, semestre, turma, turmaMock, familia, sequencia++, gerador,
                        senhaHash));
            }
            avaliar(alocacoes, daTurma, semestre, hoje, gerador);
            alunos.addAll(daTurma);
            alunosPorTurma.put(turma.getId(), daTurma);
            alocacoes.forEach(alocacao -> pedidos.add(new GradeMock.Pedido<>(alocacao, turma.getId(),
                    alocacao.getProfessor().getId(), turma.getTurno(),
                    alocacao.getDisciplina().getCargaHoraria() / HORAS_POR_AULA_SEMANAL)));
        }

        List<GradeMock.Aula<AlocacaoProfessor>> grade = GradeMock.montar(pedidos);
        horarioRepository.saveAll(grade.stream()
                .map(aula -> HorarioTurma.builder()
                        .escolaId(escolaId)
                        .alocacao(aula.alocacao())
                        .diaSemana(aula.dia())
                        .horaInicio(aula.inicio())
                        .horaFim(aula.fim())
                        .build())
                .toList());
        registrarChamadasDaSemana(grade, alunosPorTurma, semestre, hoje, extra);
        deixarNotasPorLancar(pedidos, alunosPorTurma, semestre, hoje, extra);

        lancarCobrancas(escolaId, alunos, semestre, hoje, gerador);
        lancarDespesas(escolaId, semestre, hoje, gerador);
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

    private Professor criarProfessor(UUID escolaId, ProfessorMock mock, GeradorMock gerador, String senhaHash) {
        Pessoa pessoa = criarAdulto(escolaId, mock.nome(), gerador, PapelPessoa.PROFESSOR);
        Usuario usuario = criarLogin(pessoa, PerfilUsuario.PROFESSOR, senhaHash);
        return professorRepository.save(Professor.builder()
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .usuarioId(usuario.getId())
                .formacao(mock.formacao())
                .dataAdmissao(mock.admissao())
                .vinculo(mock.vinculo())
                .ativo(true)
                .build());
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

    private AlunoMock criarAluno(UUID escolaId, Semestre semestre, Turma turma, TurmaMock turmaMock,
            Familia familia, int sequencia, GeradorMock gerador, String senhaHash) {
        boolean menina = gerador.chance(0.5);
        String nome = gerador.nomeDeFilho(menina, familia.sobrenomeMae, familia.sobrenomePai);
        int ano = semestre.inicio().getYear();
        LocalDate nascimento = LocalDate.of(ano - turmaMock.idade(), 1, 1).minusDays(gerador.entre(0, 364));
        String telefone = turmaMock.idade() >= 14 ? gerador.celular() : null;
        Pessoa pessoa = criarPessoa(escolaId, nome, gerador.cpf(), nascimento, gerador.email(nome), telefone,
                familia.endereco, PapelPessoa.ALUNO);
        Usuario usuario = criarLogin(pessoa, PerfilUsuario.ALUNO, senhaHash);

        Aluno aluno = alunoRepository.save(Aluno.builder()
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .usuarioId(usuario.getId())
                .matricula("%d-%04d".formatted(ano, sequencia))
                .genero(menina ? "Feminino" : "Masculino")
                .build());
        familia.responsaveis.forEach((responsavel, parentesco) -> alunoResponsavelRepository.save(
                AlunoResponsavel.builder()
                        .id(new AlunoResponsavelId(aluno.getId(), responsavel.getId()))
                        .parentesco(parentesco)
                        .financeiro(responsavel == familia.financeiro)
                        .build()));
        MatriculaTurma matricula = matriculaRepository.save(MatriculaTurma.builder()
                .escolaId(escolaId)
                .aluno(aluno)
                .turma(turma)
                .status(StatusMatricula.ATIVA)
                .dataMatricula(semestre.inicio().minusDays(gerador.entre(5, 45)))
                .build());

        // Media do aluno: a maioria entre 6 e 9, alguns abaixo de 6 (recuperacao).
        double desempenho = Math.clamp(7.2 + gerador.random().nextGaussian() * 1.3, 3.5, 9.8);
        return new AlunoMock(aluno, matricula, turmaMock, familia.financeiro.getId(), desempenho);
    }

    // ---- Turmas e notas ----

    private Turma criarTurma(Escola escola, PeriodoLetivo periodo, Curso curso, TurmaMock mock,
            List<Disciplina> grade) {
        Set<Disciplina> disciplinas = new LinkedHashSet<>(grade);
        // Flush ja grava TURMA_DISCIPLINA: a FK da alocacao exige a disciplina na grade da turma.
        return turmaRepository.saveAndFlush(Turma.builder()
                .escola(escola)
                .periodoLetivo(periodo)
                .curso(curso)
                .nome(mock.nome())
                .codigo(mock.codigo())
                .vagas(35)
                .turno(mock.turno())
                .ativa(true)
                .disciplinas(disciplinas)
                .build());
    }

    /** Tres avaliacoes por disciplina no semestre; as que ja passaram tem nota. */
    private void avaliar(List<AlocacaoProfessor> alocacoes, List<AlunoMock> alunos, Semestre semestre,
            LocalDate hoje, GeradorMock gerador) {
        for (AlocacaoProfessor alocacao : alocacoes) {
            avaliar(alocacao, TipoAvaliacao.PROVA, "Prova 1", semestre.inicio().plusWeeks(6), "10", alunos, hoje,
                    gerador);
            avaliar(alocacao, TipoAvaliacao.TRABALHO, "Trabalho em grupo", semestre.inicio().plusWeeks(10), "5",
                    alunos, hoje, gerador);
            avaliar(alocacao, TipoAvaliacao.PROVA, "Prova 2", semestre.fim().minusWeeks(3), "10", alunos, hoje,
                    gerador);
        }
    }

    private void avaliar(AlocacaoProfessor alocacao, TipoAvaliacao tipo, String titulo, LocalDate data,
            String valor, List<AlunoMock> alunos, LocalDate hoje, GeradorMock gerador) {
        BigDecimal pontos = new BigDecimal(valor);
        Avaliacao avaliacao = avaliacaoRepository.save(Avaliacao.builder()
                .escolaId(alocacao.getEscolaId())
                .turma(alocacao.getTurma())
                .disciplina(alocacao.getDisciplina())
                .professor(alocacao.getProfessor())
                .tipo(tipo)
                .titulo(titulo)
                .data(data)
                .valor(pontos)
                .build());
        if (!data.isBefore(hoje)) {
            return;
        }
        notaRepository.saveAll(alunos.stream()
                .map(aluno -> Nota.builder()
                        .escolaId(alocacao.getEscolaId())
                        .avaliacao(avaliacao)
                        .aluno(aluno.aluno())
                        .valor(nota(aluno.desempenho(), pontos, gerador))
                        .build())
                .toList());
    }

    /** Nota de 0 a 10 em torno do desempenho do aluno, proporcional ao valor, de meio em meio ponto. */
    private static BigDecimal nota(double desempenho, BigDecimal pontos, GeradorMock gerador) {
        double deZeroADez = Math.clamp(desempenho + gerador.random().nextGaussian() * 0.8, 0, 10);
        double proporcional = deZeroADez * pontos.doubleValue() / 10;
        return BigDecimal.valueOf(Math.round(proporcional * 2) / 2.0).setScale(1, RoundingMode.UNNECESSARY);
    }

    // ---- Grade e chamada ----

    /**
     * Chamada das aulas da grade desta semana que ja passaram (de segunda a
     * ontem), com uma ou outra falta. Uma em cada cinco fica sem chamada,
     * para o professor ter o que registrar.
     */
    private void registrarChamadasDaSemana(List<GradeMock.Aula<AlocacaoProfessor>> grade,
            Map<Long, List<AlunoMock>> alunosPorTurma, Semestre semestre, LocalDate hoje, GeradorMock gerador) {
        LocalDate segunda = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int contador = 0;
        for (GradeMock.Aula<AlocacaoProfessor> horario : grade) {
            LocalDate data = segunda.plusDays(horario.dia().ordinal());
            if (!data.isBefore(hoje) || data.isBefore(semestre.inicio()) || data.isAfter(semestre.fim())) {
                continue;
            }
            if (contador++ % 5 == 4) {
                continue;
            }
            AlocacaoProfessor alocacao = horario.alocacao();
            Aula aula = aulaRepository.save(Aula.builder()
                    .escolaId(alocacao.getEscolaId())
                    .turma(alocacao.getTurma())
                    .disciplina(alocacao.getDisciplina())
                    .professor(alocacao.getProfessor())
                    .data(data)
                    .conteudo(CONTEUDOS.get(gerador.random().nextInt(CONTEUDOS.size())))
                    .build());
            frequenciaRepository.saveAll(alunosPorTurma.get(alocacao.getTurma().getId()).stream()
                    .map(aluno -> Frequencia.builder()
                            .escolaId(alocacao.getEscolaId())
                            .aula(aula)
                            .aluno(aluno.aluno())
                            .presente(!gerador.chance(0.08))
                            .build())
                    .toList());
        }
    }

    /**
     * Uma lista de exercicios recente em cada disciplina. Um terco ja tem
     * todas as notas, um terco tem metade e o resto nenhuma: sao as "notas a
     * lancar" do professor.
     */
    private void deixarNotasPorLancar(List<GradeMock.Pedido<AlocacaoProfessor>> pedidos,
            Map<Long, List<AlunoMock>> alunosPorTurma, Semestre semestre, LocalDate hoje, GeradorMock gerador) {
        BigDecimal pontos = new BigDecimal("2");
        for (int i = 0; i < pedidos.size(); i++) {
            AlocacaoProfessor alocacao = pedidos.get(i).alocacao();
            LocalDate data = hoje.minusDays(1 + i % 6L);
            if (data.isBefore(semestre.inicio()) || data.isAfter(semestre.fim())) {
                continue;
            }
            Avaliacao avaliacao = avaliacaoRepository.save(Avaliacao.builder()
                    .escolaId(alocacao.getEscolaId())
                    .turma(alocacao.getTurma())
                    .disciplina(alocacao.getDisciplina())
                    .professor(alocacao.getProfessor())
                    .tipo(TipoAvaliacao.TAREFA)
                    .titulo("Lista de exercícios")
                    .data(data)
                    .valor(pontos)
                    .build());
            List<AlunoMock> daTurma = alunosPorTurma.get(alocacao.getTurma().getId());
            int comNota = switch (i % 3) {
                case 0 -> daTurma.size();
                case 1 -> daTurma.size() / 2;
                default -> 0;
            };
            notaRepository.saveAll(daTurma.subList(0, comNota).stream()
                    .map(aluno -> Nota.builder()
                            .escolaId(alocacao.getEscolaId())
                            .avaliacao(avaliacao)
                            .aluno(aluno.aluno())
                            .valor(nota(aluno.desempenho(), pontos, gerador))
                            .build())
                    .toList());
        }
    }

    // ---- Financeiro ----

    /**
     * Mensalidade de cada mes do semestre, material didatico no inicio e
     * passeio do 6º ano. O que venceu esta pago, menos alguns atrasos e os
     * ultimos meses de quatro familias inadimplentes.
     */
    private void lancarCobrancas(UUID escolaId, List<AlunoMock> alunos, Semestre semestre, LocalDate hoje,
            GeradorMock gerador) {
        Set<AlunoMock> inadimplentes = new LinkedHashSet<>();
        while (inadimplentes.size() < 4) {
            inadimplentes.add(alunos.get(gerador.random().nextInt(alunos.size())));
        }
        int ano = semestre.inicio().getYear();
        List<Cobranca> cobrancas = new ArrayList<>();
        for (AlunoMock aluno : alunos) {
            for (int mes = semestre.inicio().getMonthValue(); mes <= semestre.fim().getMonthValue(); mes++) {
                LocalDate vencimento = LocalDate.of(ano, mes, DIA_VENCIMENTO);
                String descricao = "Mensalidade %s/%d".formatted(
                        vencimento.getMonth().getDisplayName(TextStyle.FULL, PT_BR), ano);
                boolean atrasa = inadimplentes.contains(aluno) && !vencimento.isBefore(hoje.minusMonths(2));
                cobrancas.add(cobranca(escolaId, aluno, TipoCobranca.MENSALIDADE, descricao, aluno.turma().mensalidade(),
                        vencimento, hoje, atrasa || gerador.chance(0.03), gerador));
            }
            cobrancas.add(cobranca(escolaId, aluno, TipoCobranca.MATERIAL, "Material didático - %s".formatted(semestre.nome()),
                    new BigDecimal("420.00"), semestre.inicio().withDayOfMonth(20), hoje, gerador.chance(0.03),
                    gerador));
        }

        List<AlunoMock> sextoAno = alunos.stream().filter(a -> a.turma() == TURMAS.getFirst()).toList();
        LocalDate passeio = semestre.inicio().plusDays(52);
        for (int i = 0; i < sextoAno.size(); i++) {
            Cobranca cobranca = cobranca(escolaId, sextoAno.get(i), TipoCobranca.OUTRO,
                    "Passeio pedagógico - Museu de Ciências Naturais", new BigDecimal("95.00"), passeio, hoje, false,
                    gerador);
            if (i < 2) {
                // Desistiram do passeio antes do vencimento.
                cobranca.setStatus(StatusCobranca.CANCELADA);
                cobranca.setPagaEm(null);
                cobranca.setCanceladaEm(antesDeAgora(passeio.minusDays(7), hoje));
            }
            cobrancas.add(cobranca);
        }
        escolherFormaDasPagasOnline(cobrancas);
        cobrancaRepository.saveAll(cobrancas);
        pagamentoRepository.saveAll(pagamentosOnline(escolaId, cobrancas));
    }

    /**
     * De cada dez pagas, seis foram por PIX, tres por boleto e uma teve baixa
     * manual, para os numeros por forma terem o que mostrar. Pela posicao, sem
     * sortear: o resto da escola sai igual ao de antes.
     *
     * <p>
     * A cobranca fica NAO_EMITIDA de proposito: ela nao existe no provedor, e
     * assim nada tenta busca-la la. O comprovante sai so do pagamento, e o
     * boleto responde BOLETO_NAO_GERADO.
     * </p>
     */
    private static void escolherFormaDasPagasOnline(List<Cobranca> cobrancas) {
        List<Cobranca> pagas = cobrancas.stream().filter(c -> c.getStatus() == StatusCobranca.PAGA).toList();
        for (int i = 0; i < pagas.size(); i++) {
            int posicao = i % 10;
            if (posicao < 9) {
                pagas.get(i).setFormaPagamento(posicao < 6 ? FormaPagamento.PIX : FormaPagamento.BOLETO);
            }
        }
    }

    private static List<PagamentoCobranca> pagamentosOnline(UUID escolaId, List<Cobranca> cobrancas) {
        return cobrancas.stream()
                .filter(c -> c.getStatus() == StatusCobranca.PAGA && c.getFormaPagamento() != null)
                .map(c -> PagamentoCobranca.builder()
                        .escolaId(escolaId)
                        .cobrancaId(c.getId())
                        .valorPago(c.getValor())
                        .dataPagamento(LocalDate.ofInstant(c.getPagaEm(), ZONA))
                        .formaPagamento(c.getFormaPagamento())
                        .origem(OrigemPagamento.ONLINE)
                        .idTransacaoExterna("mock_" + c.getId())
                        .build())
                .toList();
    }

    private Cobranca cobranca(UUID escolaId, AlunoMock aluno, TipoCobranca tipo, String descricao, BigDecimal valor,
            LocalDate vencimento, LocalDate hoje, boolean atrasa, GeradorMock gerador) {
        Cobranca cobranca = Cobranca.builder()
                .escolaId(escolaId)
                .aluno(aluno.aluno())
                .matricula(aluno.matricula())
                .responsavelId(aluno.financeiroId())
                .descricao(descricao)
                .tipo(tipo)
                .valor(valor)
                .dataVencimento(vencimento)
                .status(StatusCobranca.PENDENTE)
                .build();
        if (!vencimento.isBefore(hoje)) {
            return cobranca;
        }
        if (atrasa) {
            cobranca.setStatus(StatusCobranca.EM_ATRASO);
            return cobranca;
        }
        // A maioria paga ate o vencimento; alguns poucos dias depois.
        LocalDate pagamento = gerador.chance(0.15) ? vencimento.plusDays(gerador.entre(1, 5))
                : vencimento.minusDays(gerador.entre(0, 7));
        cobranca.setStatus(StatusCobranca.PAGA);
        cobranca.setPagaEm(antesDeAgora(pagamento, hoje));
        return cobranca;
    }

    private record DespesaMock(String categoria, String descricao, String valor, int dia, boolean cancelada) {
    }

    /** Contas fixas de todo mes e algumas despesas avulsas do semestre. */
    private void lancarDespesas(UUID escolaId, Semestre semestre, LocalDate hoje, GeradorMock gerador) {
        categoriaDespesaService.listar();
        Map<String, CategoriaDespesa> categorias = categoriaDespesaRepository.findAllByEscolaIdOrderByNome(escolaId)
                .stream().collect(Collectors.toMap(CategoriaDespesa::getNome, Function.identity()));

        int ano = semestre.inicio().getYear();
        List<Despesa> despesas = new ArrayList<>();
        for (int mes = semestre.inicio().getMonthValue(); mes <= semestre.fim().getMonthValue(); mes++) {
            String nomeMes = LocalDate.of(ano, mes, 1).getMonth().getDisplayName(TextStyle.FULL, PT_BR);
            List<DespesaMock> doMes = new ArrayList<>(List.of(
                    new DespesaMock("Salários", "Folha de pagamento - " + nomeMes, "68450.00", 5, false),
                    new DespesaMock("Contas", "Aluguel do prédio - " + nomeMes, "14800.00", 10, false),
                    new DespesaMock("Contas", "Energia elétrica - " + nomeMes,
                            "%d.%02d".formatted(gerador.entre(2100, 2900), gerador.entre(0, 99)), 15, false),
                    new DespesaMock("Contas", "Água e esgoto - " + nomeMes,
                            "%d.%02d".formatted(gerador.entre(640, 900), gerador.entre(0, 99)), 18, false),
                    new DespesaMock("Contas", "Internet e telefonia - " + nomeMes, "489.90", 20, false)));
            switch (mes - semestre.inicio().getMonthValue()) {
                case 0 -> doMes.add(new DespesaMock("Materiais", "Livros para a biblioteca", "3240.00", 12, false));
                case 1 -> doMes.add(new DespesaMock("Consertos", "Manutenção dos aparelhos de ar-condicionado",
                        "1850.00", 22, false));
                case 2 -> {
                    doMes.add(new DespesaMock("Limpeza", "Produtos de limpeza e descartáveis", "1120.40", 8, false));
                    doMes.add(new DespesaMock("Consertos", "Reparo no telhado da quadra", "4600.00", 25, true));
                }
                case 3 -> doMes.add(new DespesaMock("Materiais", "Material de papelaria", "960.00", 14, false));
                case 4 -> doMes.add(new DespesaMock("Outros", semestre.nome().endsWith("/2")
                        ? "Confraternização de fim de ano" : "Festa junina", "2300.00", 12, false));
                default -> {
                    // Fevereiro a julho tem um mes a mais; so as contas fixas.
                }
            }
            for (DespesaMock mock : doMes) {
                despesas.add(despesa(escolaId, categorias.get(mock.categoria()), mock, LocalDate.of(ano, mes,
                        mock.dia()), hoje));
            }
        }
        despesaRepository.saveAll(despesas);
    }

    /** Vencida esta paga na vespera, menos a conta de luz do ultimo mes, que ficou para tras. */
    private static Despesa despesa(UUID escolaId, CategoriaDespesa categoria, DespesaMock mock,
            LocalDate vencimento, LocalDate hoje) {
        Despesa despesa = Despesa.builder()
                .escolaId(escolaId)
                .categoria(categoria)
                .descricao(mock.descricao())
                .valor(new BigDecimal(mock.valor()))
                .dataVencimento(vencimento)
                .situacao(SituacaoDespesa.A_PAGAR)
                .build();
        if (mock.cancelada()) {
            despesa.setSituacao(SituacaoDespesa.CANCELADA);
            despesa.setCanceladaEm(antesDeAgora(vencimento.minusDays(10), hoje));
        } else if (vencimento.isBefore(hoje)
                && !(mock.descricao().startsWith("Energia") && !vencimento.isBefore(hoje.minusMonths(1)))) {
            despesa.setSituacao(SituacaoDespesa.PAGA);
            despesa.setPagaEm(antesDeAgora(vencimento.minusDays(1), hoje));
        }
        return despesa;
    }

    // ---- Apoio ----

    /** Primeiro ou segundo semestre do ano de hoje. */
    private static Semestre semestreDe(LocalDate hoje) {
        int ano = hoje.getYear();
        return hoje.getMonthValue() >= 8
                ? new Semestre(ano + "/2", LocalDate.of(ano, 8, 3), LocalDate.of(ano, 12, 18))
                : new Semestre(ano + "/1", LocalDate.of(ano, 2, 2), LocalDate.of(ano, 7, 3));
    }

    /** Meio da manha do dia, sem passar de ontem: nada fica com data no futuro. */
    private static Instant antesDeAgora(LocalDate dia, LocalDate hoje) {
        LocalDate limite = dia.isBefore(hoje) ? dia : hoje.minusDays(1);
        return limite.atTime(10, 30).atZone(ZONA).toInstant();
    }

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
