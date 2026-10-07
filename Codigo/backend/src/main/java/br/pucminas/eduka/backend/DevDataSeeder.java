package br.pucminas.eduka.backend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
import br.pucminas.eduka.backend.domain.Avaliacao;
import br.pucminas.eduka.backend.domain.Curso;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Nota;
import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.PlanoEscola;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.TipoAvaliacao;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.domain.Turno;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.domain.VinculoProfessor;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.CursoRepository;
import br.pucminas.eduka.backend.repository.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.NotaRepository;
import br.pucminas.eduka.backend.repository.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Cria os usuarios de dev via JPA, nao SQL cru: PESSOA.NOME/EMAIL precisam
 * passar pelo PessoaCryptoConverter para serem cifrados de verdade. O resto do
 * mock (escolas) continua em postgres/mock/mock.sql, que nao mexe em PESSOA.
 *
 * <ul>
 * <li>Super admin: admin@eduka.com.br / Eduka-admin123</li>
 * <li>Um usuario de cada perfil de escola, todos na "Escola Dev" e com a senha
 * Eduka-dev123: master, secretaria, financeiro, professor e aluno
 * {@code @dev.eduka.com.br}. A tela /login/dev do front entra com eles.</li>
 * <li>O professor dev ganha o cadastro de PROFESSOR e turmas de exemplo
 * alocadas a ele, para o portal do professor ter o que mostrar.</li>
 * <li>O aluno dev ganha o cadastro de ALUNO, matricula no "1º Ano A" do
 * professor dev e avaliacoes com notas, para o boletim ter o que mostrar.</li>
 * </ul>
 *
 * <p>
 * Roda antes do {@link DevAcademicoSeeder}, que precisa da "Escola Dev".
 * </p>
 */
@Component
@Profile("dev")
@Order(1)
public class DevDataSeeder implements ApplicationRunner {

    private static final String LOGIN_SUPER_ADMIN = "admin@eduka.com.br";
    private static final String SENHA_SUPER_ADMIN = "Eduka-admin123";

    private static final String NOME_ESCOLA_DEV = "Escola Dev";
    private static final String SENHA_DEV = "Eduka-dev123";

    /** Perfil -> login, na ordem em que aparecem na tela /login/dev. */
    private static final Map<PerfilUsuario, String> LOGINS_DEV = new LinkedHashMap<>();

    static {
        LOGINS_DEV.put(PerfilUsuario.MASTER, "master@dev.eduka.com.br");
        LOGINS_DEV.put(PerfilUsuario.SECRETARIA, "secretaria@dev.eduka.com.br");
        LOGINS_DEV.put(PerfilUsuario.FINANCEIRO, "financeiro@dev.eduka.com.br");
        LOGINS_DEV.put(PerfilUsuario.PROFESSOR, "professor@dev.eduka.com.br");
        LOGINS_DEV.put(PerfilUsuario.ALUNO, "aluno@dev.eduka.com.br");
    }

    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final EscolaRepository escolaRepository;
    private final ProfessorRepository professorRepository;
    private final PeriodoLetivoRepository periodoLetivoRepository;
    private final CursoRepository cursoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final TurmaRepository turmaRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final AlunoRepository alunoRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final NotaRepository notaRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public DevDataSeeder(UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, EscolaRepository escolaRepository,
            ProfessorRepository professorRepository, PeriodoLetivoRepository periodoLetivoRepository,
            CursoRepository cursoRepository, DisciplinaRepository disciplinaRepository,
            TurmaRepository turmaRepository, AlocacaoProfessorRepository alocacaoRepository,
            AlunoRepository alunoRepository, MatriculaTurmaRepository matriculaRepository,
            AvaliacaoRepository avaliacaoRepository, NotaRepository notaRepository,
            PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager) {
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.escolaRepository = escolaRepository;
        this.professorRepository = professorRepository;
        this.periodoLetivoRepository = periodoLetivoRepository;
        this.cursoRepository = cursoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.turmaRepository = turmaRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.alunoRepository = alunoRepository;
        this.matriculaRepository = matriculaRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.notaRepository = notaRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Transacoes separadas: a dos usuarios roda sem escola no contexto (como
     * no login); a do professor e a do aluno precisam da escola dev como
     * tenant, e o Hibernate
     * so resolve o tenant ao abrir a sessao (ver
     * {@link br.pucminas.eduka.backend.security.EscolaTenantIdentifierResolver}).
     */
    @Override
    public void run(ApplicationArguments args) {
        transactionTemplate.executeWithoutResult(status -> criarUsuarios());

        usuarioRepository.findByLogin(LOGINS_DEV.get(PerfilUsuario.PROFESSOR))
                .ifPresent(professorDev -> comoEscola(professorDev, () -> transactionTemplate
                        .executeWithoutResult(status -> criarTurmasDoProfessor(professorDev))));

        // Depois do professor: o aluno e matriculado numa turma dele.
        usuarioRepository.findByLogin(LOGINS_DEV.get(PerfilUsuario.ALUNO))
                .ifPresent(alunoDev -> comoEscola(alunoDev, () -> transactionTemplate
                        .executeWithoutResult(status -> criarBoletimDoAluno(alunoDev))));
    }

    private void criarUsuarios() {
        if (!usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)) {
            criarUsuario("Super Admin", LOGIN_SUPER_ADMIN, SENHA_SUPER_ADMIN, PerfilUsuario.SUPER_ADMIN, null);
        }

        UUID escolaDevId = null;
        for (Map.Entry<PerfilUsuario, String> entrada : LOGINS_DEV.entrySet()) {
            PerfilUsuario perfil = entrada.getKey();
            String login = entrada.getValue();
            if (usuarioRepository.existsByLogin(login)) {
                continue;
            }
            if (escolaDevId == null) {
                escolaDevId = escolaDevId();
            }
            criarUsuario(nomeDev(perfil), login, SENHA_DEV, perfil, escolaDevId);
        }
    }

    /** Reaproveita a escola de um usuario dev ja criado; senao cria a "Escola Dev". */
    private UUID escolaDevId() {
        return LOGINS_DEV.values().stream()
                .map(usuarioRepository::findByLogin)
                .flatMap(Optional::stream)
                .map(Usuario::getEscolaId)
                .findFirst()
                .orElseGet(() -> escolaRepository.save(Escola.cadastrar(NOME_ESCOLA_DEV, null, null,
                        "contato@dev.eduka.com.br", null, PlanoEscola.ENTERPRISE, true)).getId());
    }

    private void criarUsuario(String nome, String login, String senha, PerfilUsuario perfil, UUID escolaId) {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(nome)
                .email(login)
                .ativo(true)
                .build());
        pessoaPapelRepository.save(PessoaPapel.builder()
                .pessoaId(pessoa.getId())
                .papel(papelDe(perfil))
                .build());
        usuarioRepository.save(Usuario.builder()
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .login(login)
                .senhaHash(passwordEncoder.encode(senha))
                .perfil(perfil)
                .ativo(true)
                .build());
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

    /**
     * Cadastro de PROFESSOR do usuario dev e um semestre de exemplo: duas
     * turmas ativas em 2026/2 e uma encerrada em 2026/1, para o portal ter
     * filtro de periodo e badge de encerrada. So cria as turmas se o
     * professor ainda nao tem nenhuma alocacao.
     */
    private void criarTurmasDoProfessor(Usuario usuario) {
        UUID escolaId = usuario.getEscolaId();
        Professor professor = professorRepository.findByUsuarioIdAndEscolaId(usuario.getId(), escolaId)
                .orElseGet(() -> professorRepository.save(Professor.builder()
                        .escolaId(escolaId)
                        .pessoaId(usuario.getPessoaId())
                        .usuarioId(usuario.getId())
                        .formacao("Licenciatura em Matemática")
                        .dataAdmissao(LocalDate.of(2024, 2, 1))
                        .vinculo(VinculoProfessor.CLT_40H)
                        .build()));
        if (!alocacaoRepository.findAllByEscolaIdAndProfessorId(escolaId, professor.getId()).isEmpty()) {
            return;
        }

        Escola escola = escolaRepository.getReferenceById(escolaId);
        PeriodoLetivo semestreAtual = periodoLetivoRepository.save(PeriodoLetivo.builder()
                .escola(escola).nome("2026/2")
                .dataInicio(LocalDate.of(2026, 8, 3)).dataFim(LocalDate.of(2026, 12, 18))
                .build());
        PeriodoLetivo semestreAnterior = periodoLetivoRepository.save(PeriodoLetivo.builder()
                .escola(escola).nome("2026/1")
                .dataInicio(LocalDate.of(2026, 2, 2)).dataFim(LocalDate.of(2026, 7, 3))
                .build());
        Curso ensinoMedio = cursoRepository.save(Curso.builder()
                .escola(escola).nome("Ensino Médio").codigo("EM")
                .build());
        Disciplina matematica = disciplinaRepository.save(Disciplina.builder()
                .escola(escola).curso(ensinoMedio).nome("Matemática").codigo("MAT").cargaHoraria(120)
                .build());
        Disciplina fisica = disciplinaRepository.save(Disciplina.builder()
                .escola(escola).curso(ensinoMedio).nome("Física").codigo("FIS").cargaHoraria(80)
                .build());

        Turma primeiroAno = criarTurma(escola, semestreAtual, ensinoMedio, "1º Ano A", "EM-1A", Turno.MATUTINO,
                true, Set.of(matematica, fisica));
        Turma segundoAno = criarTurma(escola, semestreAtual, ensinoMedio, "2º Ano B", "EM-2B", Turno.VESPERTINO,
                true, Set.of(matematica));
        Turma terceiroAno = criarTurma(escola, semestreAnterior, ensinoMedio, "3º Ano A", "EM-3A", Turno.MATUTINO,
                false, Set.of(matematica));

        alocar(escolaId, professor, primeiroAno, matematica);
        alocar(escolaId, professor, primeiroAno, fisica);
        alocar(escolaId, professor, segundoAno, matematica);
        alocar(escolaId, professor, terceiroAno, matematica);
    }

    /**
     * Cadastro de ALUNO do usuario dev, matriculado no "1º Ano A" do professor
     * dev, com avaliacoes de Matematica e Fisica: as ja realizadas com nota,
     * as futuras sem. So roda se o aluno ainda nao tem cadastro, e so se a
     * turma do professor dev existir.
     */
    private void criarBoletimDoAluno(Usuario usuario) {
        UUID escolaId = usuario.getEscolaId();
        if (alunoRepository.findByUsuarioIdAndEscolaId(usuario.getId(), escolaId).isPresent()) {
            return;
        }
        Optional<Turma> turma = turmaRepository.findAllByEscolaId(escolaId).stream()
                .filter(t -> "EM-1A".equals(t.getCodigo()))
                .findFirst();
        if (turma.isEmpty()) {
            return;
        }
        List<AlocacaoProfessor> alocacoes = alocacaoRepository.findAllByEscolaIdAndTurmaId(escolaId,
                turma.get().getId());

        Aluno aluno = alunoRepository.save(Aluno.builder()
                .escolaId(escolaId)
                .pessoaId(usuario.getPessoaId())
                .usuarioId(usuario.getId())
                .matricula("2026-0001")
                .build());
        matriculaRepository.save(MatriculaTurma.builder()
                .escolaId(escolaId)
                .aluno(aluno)
                .turma(turma.get())
                .status(StatusMatricula.ATIVA)
                .dataMatricula(LocalDate.of(2026, 8, 3))
                .build());

        for (AlocacaoProfessor alocacao : alocacoes) {
            boolean matematica = "MAT".equals(alocacao.getDisciplina().getCodigo());
            if (matematica) {
                avaliar(aluno, alocacao, TipoAvaliacao.PROVA, "Prova 1", LocalDate.of(2026, 9, 10), "10", "8.5");
                avaliar(aluno, alocacao, TipoAvaliacao.TRABALHO, "Trabalho em grupo", LocalDate.of(2026, 9, 24),
                        "5", "4.5");
                avaliar(aluno, alocacao, TipoAvaliacao.PROVA, "Prova 2", LocalDate.of(2026, 11, 12), "10", null);
            } else {
                avaliar(aluno, alocacao, TipoAvaliacao.TAREFA, "Lista de exercícios", LocalDate.of(2026, 9, 17),
                        "2", "2");
                avaliar(aluno, alocacao, TipoAvaliacao.PROVA, "Prova 1", LocalDate.of(2026, 10, 29), "10", null);
            }
        }
    }

    private void avaliar(Aluno aluno, AlocacaoProfessor alocacao, TipoAvaliacao tipo, String titulo,
            LocalDate data, String valor, String nota) {
        Avaliacao avaliacao = avaliacaoRepository.save(Avaliacao.builder()
                .escolaId(alocacao.getEscolaId())
                .turma(alocacao.getTurma())
                .disciplina(alocacao.getDisciplina())
                .professor(alocacao.getProfessor())
                .tipo(tipo)
                .titulo(titulo)
                .data(data)
                .valor(new BigDecimal(valor))
                .build());
        if (nota != null) {
            notaRepository.save(Nota.builder()
                    .escolaId(alocacao.getEscolaId())
                    .avaliacao(avaliacao)
                    .aluno(aluno)
                    .valor(new BigDecimal(nota))
                    .build());
        }
    }

    private Turma criarTurma(Escola escola, PeriodoLetivo periodo, Curso curso, String nome, String codigo,
            Turno turno, boolean ativa, Set<Disciplina> disciplinas) {
        Turma turma = Turma.builder()
                .escola(escola).periodoLetivo(periodo).curso(curso)
                .nome(nome).codigo(codigo).vagas(35).turno(turno).ativa(ativa)
                .build();
        turma.getDisciplinas().addAll(disciplinas);
        // Flush ja grava TURMA_DISCIPLINA: a FK da alocacao exige a disciplina na grade da turma.
        return turmaRepository.saveAndFlush(turma);
    }

    private void alocar(UUID escolaId, Professor professor, Turma turma, Disciplina disciplina) {
        alocacaoRepository.save(AlocacaoProfessor.builder()
                .escolaId(escolaId)
                .professor(professor)
                .turma(turma)
                .disciplina(disciplina)
                .build());
    }

    private static PapelPessoa papelDe(PerfilUsuario perfil) {
        return switch (perfil) {
            case ALUNO -> PapelPessoa.ALUNO;
            case PROFESSOR -> PapelPessoa.PROFESSOR;
            case SUPER_ADMIN -> PapelPessoa.USUARIO;
            default -> PapelPessoa.FUNCIONARIO;
        };
    }

    private static String nomeDev(PerfilUsuario perfil) {
        String nome = perfil.name().charAt(0) + perfil.name().substring(1).toLowerCase();
        return nome + " Dev";
    }
}
