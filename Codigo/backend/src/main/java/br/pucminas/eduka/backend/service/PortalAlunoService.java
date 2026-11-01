package br.pucminas.eduka.backend.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Avaliacao;
import br.pucminas.eduka.backend.domain.Disciplina;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Nota;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.dto.AvaliacaoDoAlunoResponse;
import br.pucminas.eduka.backend.dto.BoletimTurmaResponse;
import br.pucminas.eduka.backend.dto.BoletimTurmaResponse.BoletimDisciplina;
import br.pucminas.eduka.backend.dto.BoletimTurmaResponse.Frequencia;
import br.pucminas.eduka.backend.dto.TurmaDoAlunoResponse;
import br.pucminas.eduka.backend.dto.TurmaDoAlunoResponse.DisciplinaDaTurma;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.FrequenciaRepository.FrequenciaDaDisciplina;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.NotaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
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
                                        avaliacoesPorDisciplina.getOrDefault(chave, List.of()), notas, frequencia);
                            })
                            .toList();
                    Frequencia geral = Frequencia
                            .somar(disciplinas.stream().map(BoletimDisciplina::frequencia).toList());
                    return new BoletimTurmaResponse(turma.getId(), turma.getNome(),
                            turma.getPeriodoLetivo().getNome(), turma.getCurso().getNome(), m.getStatus(),
                            disciplinas, geral, BoletimTurmaResponse.LIMITE_FALTAS_PERCENTUAL);
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
            List<Avaliacao> avaliacoes, Map<UUID, Nota> notas, Frequencia frequencia) {
        BigDecimal obtidos = BigDecimal.ZERO;
        BigDecimal avaliados = BigDecimal.ZERO;
        BigDecimal distribuidos = BigDecimal.ZERO;
        List<Avaliacao> ordenadas = avaliacoes.stream().sorted(POR_DATA).toList();
        for (Avaliacao avaliacao : ordenadas) {
            distribuidos = distribuidos.add(avaliacao.getValor());
            Nota nota = notas.get(avaliacao.getId());
            if (nota != null) {
                obtidos = obtidos.add(nota.getValor());
                avaliados = avaliados.add(avaliacao.getValor());
            }
        }
        List<AvaliacaoDoAlunoResponse> itens = ordenadas.stream()
                .map(a -> AvaliacaoDoAlunoResponse.de(a, notas.get(a.getId())))
                .toList();
        return new BoletimDisciplina(disciplina.getId(), disciplina.getNome(), professorNome, itens, obtidos,
                avaliados, distribuidos, frequencia);
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

    private Map<UUID, Nota> notasPorAvaliacao(Aluno aluno, UUID escolaId) {
        return notaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId()).stream()
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
