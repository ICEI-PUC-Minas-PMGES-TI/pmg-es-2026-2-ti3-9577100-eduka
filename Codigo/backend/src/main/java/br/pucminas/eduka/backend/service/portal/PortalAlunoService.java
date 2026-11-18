package br.pucminas.eduka.backend.service.portal;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.Avaliacao;
import br.pucminas.eduka.backend.domain.academico.Bimestres;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.Nota;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.academico.AvaliacaoDoAlunoResponse;
import br.pucminas.eduka.backend.dto.academico.BoletimTurmaResponse.BimestreDaDisciplina;
import br.pucminas.eduka.backend.dto.academico.BoletimTurmaResponse.BimestreDaTurma;
import br.pucminas.eduka.backend.dto.academico.BoletimTurmaResponse.BoletimDisciplina;
import br.pucminas.eduka.backend.dto.academico.BoletimTurmaResponse.Frequencia;
import br.pucminas.eduka.backend.dto.academico.BoletimTurmaResponse;
import br.pucminas.eduka.backend.dto.academico.TurmaDoAlunoResponse.DisciplinaDaTurma;
import br.pucminas.eduka.backend.dto.academico.TurmaDoAlunoResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository.FrequenciaDaDisciplina;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository.FrequenciaDoDia;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.NotaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Area academica do aluno autenticado: turmas, boletim e agenda de
 * avaliacoes (RF23). O aluno e a escola saem do token; nao ha como pedir
 * dados de outro aluno por aqui.
 *
 * <p>
 * Turmas e boletim consideram as matriculas ATIVA, TRANCADA e CONCLUIDA
 * (historico); CANCELADA fica de fora. A agenda so olha as turmas em que o
 * aluno esta ATIVO.
 * </p>
 */
@Service
public class PortalAlunoService {

    private static final Set<StatusMatricula> SITUACOES_DO_HISTORICO = Set.of(StatusMatricula.ATIVA,
            StatusMatricula.TRANCADA, StatusMatricula.CONCLUIDA);

    private static final Comparator<MatriculaTurma> ATIVAS_PRIMEIRO = Comparator
            .comparing((MatriculaTurma m) -> m.getStatus() != StatusMatricula.ATIVA)
            .thenComparing(m -> m.getTurma().getNome(), String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<Avaliacao> POR_DATA = Comparator.comparing(Avaliacao::getData)
            .thenComparing(Avaliacao::getTitulo, String.CASE_INSENSITIVE_ORDER);

    private final AlunoRepository alunoRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final NotaRepository notaRepository;
    private final FrequenciaRepository frequenciaRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;

    public PortalAlunoService(AlunoRepository alunoRepository, MatriculaTurmaRepository matriculaRepository,
            AlocacaoProfessorRepository alocacaoRepository, AvaliacaoRepository avaliacaoRepository,
            NotaRepository notaRepository, FrequenciaRepository frequenciaRepository,
            PessoaRepository pessoaRepository, IsolamentoEscola isolamentoEscola) {
        this.alunoRepository = alunoRepository;
        this.matriculaRepository = matriculaRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.notaRepository = notaRepository;
        this.frequenciaRepository = frequenciaRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    /** Turmas do aluno (ativas primeiro), com a grade e os professores. */
    @Transactional(readOnly = true)
    public List<TurmaDoAlunoResponse> listarMinhasTurmas() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<MatriculaTurma> matriculas = matriculas(alunoAtual(escolaId), escolaId);
        Map<String, String> professores = professoresPorTurmaEDisciplina(matriculas, escolaId);

        return matriculas.stream()
                .sorted(ATIVAS_PRIMEIRO)
                .map(m -> {
                    Turma turma = m.getTurma();
                    List<DisciplinaDaTurma> disciplinas = gradeOrdenada(turma).stream()
                            .map(d -> new DisciplinaDaTurma(d.getId(), d.getNome(), d.getCodigo(),
                                    d.getCargaHoraria(), professores.get(chave(turma.getId(), d.getId()))))
                            .toList();
                    return new TurmaDoAlunoResponse(m.getId(), m.getStatus(), m.getDataMatricula(), turma.getId(),
                            turma.getNome(), turma.getCodigo(), turma.getTurno(), turma.getPeriodoLetivo().getNome(),
                            turma.getCurso().getNome(), disciplinas);
                })
                .toList();
    }

    /**
     * Boletim por turma: cada disciplina da grade, com as avaliacoes (por
     * data), a nota do aluno em cada uma, os totais de pontos e a frequencia
     * (aulas com chamada, faltas e alerta acima de 25%). Disciplina sem aula
     * registrada vem com frequencia nula ("sem aulas"), nunca 0% ou 100%.
     *
     * <p>
     * Tudo vem tambem separado por bimestre (ver {@link Bimestres}): cada
     * avaliacao cai no bimestre da data dela, e cada aula, no da data da aula.
     * </p>
     */
    @Transactional(readOnly = true)
    public List<BoletimTurmaResponse> boletim() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoAtual(escolaId);
        List<MatriculaTurma> matriculas = matriculas(aluno, escolaId);
        if (matriculas.isEmpty()) {
            return List.of();
        }
        Map<String, String> professores = professoresPorTurmaEDisciplina(matriculas, escolaId);
        Map<String, List<Avaliacao>> avaliacoesPorDisciplina = avaliacoes(matriculas, escolaId).stream()
                .collect(Collectors.groupingBy(a -> chave(a.getTurma().getId(), a.getDisciplina().getId())));
        Map<UUID, Nota> notas = notasPorAvaliacao(aluno, escolaId);

        return matriculas.stream()
                .sorted(ATIVAS_PRIMEIRO)
                .map(m -> {
                    Turma turma = m.getTurma();
                    PeriodoLetivo periodo = turma.getPeriodoLetivo();
                    Bimestres bimestres = Bimestres.doPeriodo(periodo);
                    Map<Long, Map<Integer, Frequencia>> frequenciasPorBimestre = frequenciasPorBimestre(escolaId,
                            aluno, turma, bimestres);
                    Map<Long, FrequenciaDaDisciplina> frequencias = frequenciaRepository
                            .resumirPorDisciplina(escolaId, aluno.getId(), turma.getId()).stream()
                            .collect(Collectors.toMap(FrequenciaDaDisciplina::getDisciplinaId,
                                    Function.identity()));
                    List<BoletimDisciplina> disciplinas = gradeOrdenada(turma).stream()
                            .map(d -> {
                                String chave = chave(turma.getId(), d.getId());
                                FrequenciaDaDisciplina resumo = frequencias.get(d.getId());
                                Frequencia frequencia = resumo == null ? null
                                        : Frequencia.de(resumo.getAulas(), resumo.getFaltas());
                                return boletimDaDisciplina(d, professores.get(chave),
                                        avaliacoesPorDisciplina.getOrDefault(chave, List.of()), notas, frequencia,
                                        bimestres, frequenciasPorBimestre.getOrDefault(d.getId(), Map.of()));
                            })
                            .toList();
                    Frequencia geral = Frequencia
                            .somar(disciplinas.stream().map(BoletimDisciplina::frequencia).toList());
                    List<BimestreDaTurma> bimestresDaTurma = bimestres.lista().stream()
                            .map(b -> new BimestreDaTurma(b.numero(), b.inicio(), b.fim(), Frequencia.somar(
                                    disciplinas.stream().map(d -> frequenciaNoBimestre(d, b.numero())).toList())))
                            .toList();
                    return new BoletimTurmaResponse(turma.getId(), turma.getNome(), periodo.getNome(),
                            turma.getCurso().getNome(), m.getStatus(), disciplinas, geral,
                            BoletimTurmaResponse.LIMITE_FALTAS_PERCENTUAL,
                            periodo.getDataInicio() == null ? null : periodo.getDataInicio().getYear(),
                            periodo.getDataInicio(), periodo.getDataFim(), bimestresDaTurma);
                })
                .toList();
    }

    /** Agenda: avaliacoes das turmas em que o aluno esta ATIVO, por data. */
    @Transactional(readOnly = true)
    public List<AvaliacaoDoAlunoResponse> agenda() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoAtual(escolaId);
        List<MatriculaTurma> ativas = matriculas(aluno, escolaId).stream()
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .toList();
        if (ativas.isEmpty()) {
            return List.of();
        }
        Map<UUID, Nota> notas = notasPorAvaliacao(aluno, escolaId);
        return avaliacoes(ativas, escolaId).stream()
                .sorted(POR_DATA)
                .map(a -> AvaliacaoDoAlunoResponse.de(a, notas.get(a.getId())))
                .toList();
    }

    private static BoletimDisciplina boletimDaDisciplina(Disciplina disciplina, String professorNome,
            List<Avaliacao> avaliacoes, Map<UUID, Nota> notas, Frequencia frequencia, Bimestres bimestres,
            Map<Integer, Frequencia> frequenciasPorBimestre) {
        Totais geral = new Totais();
        Map<Integer, Totais> porBimestre = new HashMap<>();
        List<Avaliacao> ordenadas = avaliacoes.stream().sorted(POR_DATA).toList();
        for (Avaliacao avaliacao : ordenadas) {
            Nota nota = notas.get(avaliacao.getId());
            geral.somar(avaliacao, nota);
            Integer bimestre = bimestres.numeroDe(avaliacao.getData());
            if (bimestre != null) {
                porBimestre.computeIfAbsent(bimestre, n -> new Totais()).somar(avaliacao, nota);
            }
        }
        List<AvaliacaoDoAlunoResponse> itens = ordenadas.stream()
                .map(a -> AvaliacaoDoAlunoResponse.de(a, notas.get(a.getId()), bimestres))
                .toList();
        List<BimestreDaDisciplina> bimestresDaDisciplina = bimestres.lista().stream()
                .map(b -> {
                    Totais t = porBimestre.getOrDefault(b.numero(), new Totais());
                    return new BimestreDaDisciplina(b.numero(), t.obtidos, t.avaliados, t.distribuidos,
                            frequenciasPorBimestre.get(b.numero()));
                })
                .toList();
        return new BoletimDisciplina(disciplina.getId(), disciplina.getNome(), professorNome, itens, geral.obtidos,
                geral.avaliados, geral.distribuidos, frequencia, bimestresDaDisciplina);
    }

    /** Pontos somados de um conjunto de avaliacoes: a disciplina toda ou um bimestre. */
    private static final class Totais {
        private BigDecimal obtidos = BigDecimal.ZERO;
        private BigDecimal avaliados = BigDecimal.ZERO;
        private BigDecimal distribuidos = BigDecimal.ZERO;

        void somar(Avaliacao avaliacao, Nota nota) {
            distribuidos = distribuidos.add(avaliacao.getValor());
            if (nota != null) {
                obtidos = obtidos.add(nota.getValor());
                avaliados = avaliados.add(avaliacao.getValor());
            }
        }
    }

    /**
     * Frequencia do aluno em cada disciplina e bimestre da turma, pelo dia de
     * cada aula; bimestre sem aula com chamada fica de fora (frequencia nula).
     */
    private Map<Long, Map<Integer, Frequencia>> frequenciasPorBimestre(UUID escolaId, Aluno aluno, Turma turma,
            Bimestres bimestres) {
        if (bimestres.lista().isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Integer, long[]>> aulasEFaltas = new HashMap<>();
        for (FrequenciaDoDia dia : frequenciaRepository.resumirPorDisciplinaEDia(escolaId, aluno.getId(),
                turma.getId())) {
            long[] soma = aulasEFaltas.computeIfAbsent(dia.getDisciplinaId(), d -> new HashMap<>())
                    .computeIfAbsent(bimestres.numeroDe(dia.getData()), n -> new long[2]);
            soma[0] += dia.getAulas();
            soma[1] += dia.getFaltas();
        }
        Map<Long, Map<Integer, Frequencia>> frequencias = new HashMap<>();
        aulasEFaltas.forEach((disciplina, porBimestre) -> porBimestre.forEach((bimestre, soma) -> frequencias
                .computeIfAbsent(disciplina, d -> new HashMap<>())
                .put(bimestre, Frequencia.de(soma[0], soma[1]))));
        return frequencias;
    }

    private static Frequencia frequenciaNoBimestre(BoletimDisciplina disciplina, int bimestre) {
        return disciplina.bimestres().stream()
                .filter(b -> b.numero() == bimestre)
                .findFirst()
                .map(BimestreDaDisciplina::frequencia)
                .orElse(null);
    }

    /** Aluno dono do login. 404 se o login nao e de um aluno da escola. */
    private Aluno alunoAtual(UUID escolaId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        return alunoRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
    }

    private List<MatriculaTurma> matriculas(Aluno aluno, UUID escolaId) {
        return matriculaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId()).stream()
                .filter(m -> SITUACOES_DO_HISTORICO.contains(m.getStatus()))
                .toList();
    }

    private List<Avaliacao> avaliacoes(List<MatriculaTurma> matriculas, UUID escolaId) {
        return avaliacaoRepository.findAllByEscolaIdAndTurmaIdIn(escolaId, turmaIds(matriculas));
    }

    /** So as notas publicadas: em rascunho (IU14), o aluno ainda nao ve a nota. */
    private Map<UUID, Nota> notasPorAvaliacao(Aluno aluno, UUID escolaId) {
        return notaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId()).stream()
                .filter(n -> n.getAvaliacao().isNotasPublicadas())
                .collect(Collectors.toMap(n -> n.getAvaliacao().getId(), Function.identity()));
    }

    /** Nome do professor alocado em cada par turma/disciplina, pela chave de {@link #chave}. */
    private Map<String, String> professoresPorTurmaEDisciplina(List<MatriculaTurma> matriculas, UUID escolaId) {
        if (matriculas.isEmpty()) {
            return Map.of();
        }
        List<AlocacaoProfessor> alocacoes = alocacaoRepository
                .findAllComProfessorByEscolaIdAndTurmaIdIn(escolaId, turmaIds(matriculas));
        if (alocacoes.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> nomesPorPessoa = pessoaRepository
                .findAllByIdInAndEscolaId(alocacoes.stream().map(a -> a.getProfessor().getPessoaId()).distinct()
                        .toList(), escolaId)
                .stream().collect(Collectors.toMap(Pessoa::getId, Pessoa::getNome));
        return alocacoes.stream()
                .filter(a -> nomesPorPessoa.containsKey(a.getProfessor().getPessoaId()))
                .collect(Collectors.toMap(a -> chave(a.getTurma().getId(), a.getDisciplina().getId()),
                        a -> nomesPorPessoa.get(a.getProfessor().getPessoaId())));
    }

    private static List<Long> turmaIds(List<MatriculaTurma> matriculas) {
        return matriculas.stream().map(m -> m.getTurma().getId()).distinct().toList();
    }

    private static List<Disciplina> gradeOrdenada(Turma turma) {
        return turma.getDisciplinas().stream()
                .sorted(Comparator.comparing(Disciplina::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String chave(Long turmaId, Long disciplinaId) {
        return turmaId + ":" + disciplinaId;
    }
}
