package br.pucminas.eduka.backend;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.Curso;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.repository.academico.CursoRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;

/**
 * Estrutura academica de exemplo na "Escola Dev": um periodo letivo, dois
 * cursos, disciplinas e turmas com a grade montada. Existe porque a API ainda
 * so lista turmas, nao cadastra, e a tela de alocacao de professores precisa
 * de turmas para funcionar em dev.
 *
 * <p>
 * Os professores nao entram aqui: sao cadastrados pela tela de professores da
 * secretaria. So semeia quando a escola ainda nao tem turma nenhuma.
 * </p>
 */
@Component
@Profile("dev")
@Order(2)
public class DevAcademicoSeeder implements ApplicationRunner {

    /** Criado pelo {@link DevDataSeeder}; e por ele que se acha a "Escola Dev". */
    private static final String LOGIN_SECRETARIA_DEV = "secretaria@dev.eduka.com.br";

    private static final String MATEMATICA = "Matemática";
    private static final String PORTUGUES = "Língua Portuguesa";
    private static final String FISICA = "Física";
    private static final String CIENCIAS = "Ciências";
    private static final String HISTORIA = "História";
    private static final String EDUCACAO_FISICA = "Educação Física";

    /** Disciplina -> carga horaria, na ordem em que sao criadas. */
    private static final Map<String, Integer> DISCIPLINAS = new LinkedHashMap<>();

    static {
        DISCIPLINAS.put(MATEMATICA, 160);
        DISCIPLINAS.put(PORTUGUES, 160);
        DISCIPLINAS.put(FISICA, 80);
        DISCIPLINAS.put(CIENCIAS, 120);
        DISCIPLINAS.put(HISTORIA, 80);
        DISCIPLINAS.put(EDUCACAO_FISICA, 80);
    }

    private static final List<String> GRADE_MEDIO = List.of(MATEMATICA, PORTUGUES, FISICA, HISTORIA,
            EDUCACAO_FISICA);
    private static final List<String> GRADE_FUNDAMENTAL = List.of(MATEMATICA, PORTUGUES, CIENCIAS, HISTORIA,
            EDUCACAO_FISICA);

    private final UsuarioRepository usuarioRepository;
    private final EscolaRepository escolaRepository;
    private final PeriodoLetivoRepository periodoLetivoRepository;
    private final CursoRepository cursoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final TurmaRepository turmaRepository;

    public DevAcademicoSeeder(UsuarioRepository usuarioRepository, EscolaRepository escolaRepository,
            PeriodoLetivoRepository periodoLetivoRepository, CursoRepository cursoRepository,
            DisciplinaRepository disciplinaRepository, TurmaRepository turmaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.escolaRepository = escolaRepository;
        this.periodoLetivoRepository = periodoLetivoRepository;
        this.cursoRepository = cursoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.turmaRepository = turmaRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        usuarioRepository.findByLogin(LOGIN_SECRETARIA_DEV)
                .map(usuario -> usuario.getEscolaId())
                .filter(escolaId -> turmaRepository.findAllByEscolaId(escolaId).isEmpty())
                .map(escolaRepository::getReferenceById)
                .ifPresent(this::semear);
    }

    private void semear(Escola escola) {
        PeriodoLetivo periodo = periodoLetivoRepository.save(PeriodoLetivo.builder()
                .escola(escola)
                .nome("2026/2")
                .dataInicio(LocalDate.of(2026, 8, 3))
                .dataFim(LocalDate.of(2026, 12, 18))
                .build());
        Curso medio = cursoRepository.save(Curso.builder().escola(escola).nome("Ensino Médio").codigo("EM").build());
        Curso fundamental = cursoRepository
                .save(Curso.builder().escola(escola).nome("Fundamental II").codigo("EF2").build());

        Map<String, Disciplina> disciplinas = new LinkedHashMap<>();
        DISCIPLINAS.forEach((nome, cargaHoraria) -> disciplinas.put(nome, disciplinaRepository
                .save(Disciplina.builder().escola(escola).nome(nome).cargaHoraria(cargaHoraria).build())));

        criarTurma(escola, periodo, medio, "2º ano A", Turno.MATUTINO, GRADE_MEDIO, disciplinas);
        criarTurma(escola, periodo, medio, "2º ano B", Turno.VESPERTINO, GRADE_MEDIO, disciplinas);
        criarTurma(escola, periodo, medio, "1º ano C", Turno.MATUTINO, GRADE_MEDIO, disciplinas);
        criarTurma(escola, periodo, fundamental, "8º ano B", Turno.VESPERTINO, GRADE_FUNDAMENTAL, disciplinas);
        criarTurma(escola, periodo, fundamental, "6º ano A", Turno.MATUTINO, GRADE_FUNDAMENTAL, disciplinas);
    }

    private void criarTurma(Escola escola, PeriodoLetivo periodo, Curso curso, String nome, Turno turno,
            List<String> grade, Map<String, Disciplina> disciplinas) {
        Set<Disciplina> daTurma = new LinkedHashSet<>();
        grade.forEach(disciplina -> daTurma.add(disciplinas.get(disciplina)));
        turmaRepository.save(Turma.builder()
                .escola(escola)
                .periodoLetivo(periodo)
                .curso(curso)
                .nome(nome)
                .vagas(35)
                .turno(turno)
                .disciplinas(daTurma)
                .build());
    }
}
