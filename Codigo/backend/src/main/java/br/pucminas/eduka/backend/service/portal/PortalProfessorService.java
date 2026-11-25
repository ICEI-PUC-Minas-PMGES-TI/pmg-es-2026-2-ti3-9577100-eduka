package br.pucminas.eduka.backend.service.portal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.HorarioTurma;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.dto.academico.AlunoDaTurmaResponse;
import br.pucminas.eduka.backend.dto.academico.TurmaDoProfessorResponse.DisciplinaDoProfessor;
import br.pucminas.eduka.backend.dto.academico.TurmaDoProfessorResponse.HorarioDaDisciplina;
import br.pucminas.eduka.backend.dto.academico.TurmaDoProfessorResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.HorarioTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
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
    private final HorarioTurmaRepository horarioRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;

    public PortalProfessorService(ProfessorRepository professorRepository,
            AlocacaoProfessorRepository alocacaoRepository, HorarioTurmaRepository horarioRepository,
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
     * disciplinas dele, o horario de cada uma (IU13) e quantos alunos ativos
     * ela tem. Ordenado por nome da
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
        // Aulas da grade semanal (HORARIO_TURMA) das alocacoes dele, por alocacao, em ordem de dia e hora.
        Map<UUID, List<HorarioDaDisciplina>> horarios = alocacoes.isEmpty() ? Map.of()
                : horarioRepository.findAllByEscolaIdAndAlocacao_IdIn(escolaId,
                        alocacoes.stream().map(AlocacaoProfessor::getId).toList()).stream()
                        .sorted(Comparator.comparing(HorarioTurma::getDiaSemana)
                                .thenComparing(HorarioTurma::getHoraInicio))
                        .collect(Collectors.groupingBy(h -> h.getAlocacao().getId(),
                                Collectors.mapping(HorarioDaDisciplina::de, Collectors.toList())));

        // Alunos ativos de cada turma, contados no banco numa query so (turma sem aluno fica de fora).
        Map<Long, Long> alunosAtivos = alocacoes.isEmpty() ? Map.of()
                : matriculaRepository.contarAtivasPorTurma(escolaId).stream()
                        .collect(Collectors.toMap(MatriculaTurmaRepository.AtivasNaTurma::getTurmaId,
                                MatriculaTurmaRepository.AtivasNaTurma::getQuantidade));

        return porTurma.values().stream()
                .map(daTurma -> paraResposta(daTurma, horarios, alunosAtivos))
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
            Map<UUID, List<HorarioDaDisciplina>> horarios, Map<Long, Long> alunosAtivos) {
        Turma turma = alocacoesDaTurma.get(0).getTurma();
        List<DisciplinaDoProfessor> disciplinas = alocacoesDaTurma.stream()
                .map(alocacao -> DisciplinaDoProfessor.de(alocacao.getDisciplina(),
                        horarios.getOrDefault(alocacao.getId(), List.of())))
                .sorted(Comparator.comparing(DisciplinaDoProfessor::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return TurmaDoProfessorResponse.de(turma, alunosAtivos.getOrDefault(turma.getId(), 0L), disciplinas);
    }
}
