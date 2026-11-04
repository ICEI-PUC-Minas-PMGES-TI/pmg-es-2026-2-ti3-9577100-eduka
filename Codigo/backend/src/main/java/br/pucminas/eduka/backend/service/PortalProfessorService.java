package br.pucminas.eduka.backend.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.dto.AlunoDaTurmaResponse;
import br.pucminas.eduka.backend.dto.HorarioAulaResponse;
import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse;
import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse.DisciplinaDoProfessor;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.HorarioAulaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Area pessoal do professor: o que ele enxerga e sempre o que esta alocado
 * a ele (ver {@link AlocacaoProfessorService}).
 *
 * <p>
 * O professor nunca e parametro: sai do login do token, assim como a escola.
 * Nao ha como pedir as turmas de outro professor por aqui.
 * </p>
 */
@Service
public class PortalProfessorService {

    /** Quem aparece no Ver alunos: quem esta na turma, ativo ou trancado. */
    private static final Set<StatusMatricula> NA_TURMA = Set.of(StatusMatricula.ATIVA, StatusMatricula.TRANCADA);

    private final ProfessorRepository professorRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final HorarioAulaRepository horarioRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;

    public PortalProfessorService(ProfessorRepository professorRepository,
            AlocacaoProfessorRepository alocacaoRepository, HorarioAulaRepository horarioRepository,
            MatriculaTurmaRepository matriculaRepository, PessoaRepository pessoaRepository,
            IsolamentoEscola isolamentoEscola) {
        this.professorRepository = professorRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.horarioRepository = horarioRepository;
        this.matriculaRepository = matriculaRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    /**
     * Turmas em que o professor autenticado leciona, cada uma so com as
     * disciplinas dele e o horario de cada uma (IU13). Ordenado por nome da
     * turma e da disciplina; sem alocacao, lista vazia.
     *
     * @throws RecursoNaoEncontradoException se o login nao e de um professor
     *         cadastrado na escola
     */
    @Transactional(readOnly = true)
    public List<TurmaDoProfessorResponse> listarMinhasTurmas() {
        UUID escolaId = isolamentoEscola.usuarioAtual().escolaId();
        List<AlocacaoProfessor> alocacoes = minhasAlocacoes(escolaId);

        Map<Long, List<AlocacaoProfessor>> porTurma = alocacoes.stream()
                .collect(Collectors.groupingBy(alocacao -> alocacao.getTurma().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        // Horarios das turmas, por turma e disciplina.
        Map<String, List<HorarioAulaResponse>> horarios = porTurma.isEmpty() ? Map.of()
                : horarioRepository.findAllByEscolaIdAndTurmaIdInOrderByDiaSemanaAscHoraInicioAsc(escolaId,
                        porTurma.keySet()).stream()
                        .collect(Collectors.groupingBy(h -> h.getTurmaId() + "|" + h.getDisciplinaId(),
                                Collectors.mapping(HorarioAulaResponse::de, Collectors.toList())));

        return porTurma.values().stream()
                .map(daTurma -> paraResposta(daTurma, horarios))
                .sorted(Comparator.comparing(TurmaDoProfessorResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Alunos da turma (Ver alunos, IU13), ativos ou trancados, por nome. So de
     * turma em que o professor leciona.
     *
     * @throws RecursoNaoEncontradoException se a turma nao e do professor
     */
    @Transactional(readOnly = true)
    public List<AlunoDaTurmaResponse> alunosDaTurma(Long turmaId) {
        UUID escolaId = isolamentoEscola.usuarioAtual().escolaId();
        boolean leciona = minhasAlocacoes(escolaId).stream().anyMatch(a -> a.getTurma().getId().equals(turmaId));
        if (!leciona) {
            throw new RecursoNaoEncontradoException("Turma nao encontrada");
        }
        List<MatriculaTurma> matriculas = matriculaRepository.findAllByEscolaIdAndTurmaId(escolaId, turmaId).stream()
                .filter(m -> NA_TURMA.contains(m.getStatus()))
                .toList();
        Map<UUID, String> nomes = pessoaRepository
                .findAllByIdInAndEscolaId(matriculas.stream().map(m -> m.getAluno().getPessoaId()).toList(), escolaId)
                .stream()
                .collect(Collectors.toMap(Pessoa::getId, Pessoa::getNome));
        return matriculas.stream()
                .map(m -> new AlunoDaTurmaResponse(m.getAluno().getId(), nomes.get(m.getAluno().getPessoaId()),
                        m.getAluno().getMatricula(), m.getStatus()))
                .sorted(Comparator.comparing(a -> a.nome() == null ? "" : a.nome(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<AlocacaoProfessor> minhasAlocacoes(UUID escolaId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        Professor professor = professorRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor nao encontrado"));
        return alocacaoRepository.findAllComTurmaEDisciplinaByEscolaIdAndProfessorId(escolaId, professor.getId());
    }

    private static TurmaDoProfessorResponse paraResposta(List<AlocacaoProfessor> alocacoesDaTurma,
            Map<String, List<HorarioAulaResponse>> horarios) {
        Turma turma = alocacoesDaTurma.get(0).getTurma();
        List<DisciplinaDoProfessor> disciplinas = alocacoesDaTurma.stream()
                .map(alocacao -> DisciplinaDoProfessor.de(alocacao.getDisciplina(),
                        horarios.getOrDefault(turma.getId() + "|" + alocacao.getDisciplina().getId(), List.of())))
                .sorted(Comparator.comparing(DisciplinaDoProfessor::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return TurmaDoProfessorResponse.de(turma, disciplinas);
    }
}
