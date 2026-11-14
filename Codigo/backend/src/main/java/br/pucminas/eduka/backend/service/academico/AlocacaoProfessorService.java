package br.pucminas.eduka.backend.service.academico;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.dto.academico.AlocacaoProfessorRequest;
import br.pucminas.eduka.backend.dto.academico.AlocacaoProfessorResponse;
import br.pucminas.eduka.backend.exception.academico.AlocacaoJaExisteException;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Alocacao de professores nas disciplinas das turmas, com isolamento por
 * escola (RF05, RNF03): professor, turma e disciplina sao sempre buscados
 * com a escola do token, e os de outra escola respondem 404.
 *
 * <p>
 * Regras: so professor ativo e turma ativa recebem alocacao; a disciplina
 * precisa estar na grade da turma; cada disciplina tem um professor por
 * turma (409 se ja houver). Trocar o professor e desalocar e alocar de novo.
 * </p>
 */
@Service
public class AlocacaoProfessorService {

    private final AlocacaoProfessorRepository alocacaoRepository;
    private final ProfessorRepository professorRepository;
    private final PessoaRepository pessoaRepository;
    private final TurmaRepository turmaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final IsolamentoEscola isolamentoEscola;

    public AlocacaoProfessorService(AlocacaoProfessorRepository alocacaoRepository,
            ProfessorRepository professorRepository, PessoaRepository pessoaRepository,
            TurmaRepository turmaRepository, DisciplinaRepository disciplinaRepository,
            IsolamentoEscola isolamentoEscola) {
        this.alocacaoRepository = alocacaoRepository;
        this.professorRepository = professorRepository;
        this.pessoaRepository = pessoaRepository;
        this.turmaRepository = turmaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    @Transactional(readOnly = true)
    public List<AlocacaoProfessorResponse> listarPorProfessor(UUID professorId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(professorId, escolaId);
        Pessoa pessoa = buscarPessoa(professor, escolaId);
        return alocacaoRepository.findAllByEscolaIdAndProfessorId(escolaId, professor.getId()).stream()
                .map(alocacao -> AlocacaoProfessorResponse.de(alocacao, pessoa))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlocacaoProfessorResponse> listarPorTurma(Long turmaId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Turma turma = buscarTurma(turmaId, escolaId);
        List<AlocacaoProfessor> alocacoes = alocacaoRepository.findAllByEscolaIdAndTurmaId(escolaId, turma.getId());
        if (alocacoes.isEmpty()) {
            return List.of();
        }
        List<UUID> pessoaIds = alocacoes.stream().map(alocacao -> alocacao.getProfessor().getPessoaId()).toList();
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Function.identity()));
        return alocacoes.stream()
                .map(alocacao -> AlocacaoProfessorResponse.de(alocacao,
                        pessoasPorId.get(alocacao.getProfessor().getPessoaId())))
                .toList();
    }

    /**
     * Aloca o professor na disciplina da turma. O request nao carrega escola:
     * turma e disciplina de outra escola respondem 404, como inexistentes.
     *
     * @throws AlocacaoJaExisteException se a disciplina ja tem professor na turma
     */
    @Transactional
    public AlocacaoProfessorResponse alocar(UUID professorId, AlocacaoProfessorRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(professorId, escolaId);
        if (!professor.isAtivo()) {
            throw new IllegalArgumentException("Professor inativo nao pode ser alocado");
        }
        Turma turma = buscarTurma(request.turmaId(), escolaId);
        if (!Boolean.TRUE.equals(turma.getAtiva())) {
            throw new IllegalArgumentException("Turma inativa nao recebe alocacao");
        }
        Disciplina disciplina = disciplinaRepository.findByIdAndEscolaId(request.disciplinaId(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Disciplina nao encontrada"));
        boolean naGrade = turma.getDisciplinas().stream().anyMatch(d -> d.getId().equals(disciplina.getId()));
        if (!naGrade) {
            throw new IllegalArgumentException("Disciplina nao faz parte da turma");
        }
        if (alocacaoRepository.existsByEscolaIdAndTurmaIdAndDisciplinaId(escolaId, turma.getId(),
                disciplina.getId())) {
            throw new AlocacaoJaExisteException();
        }

        AlocacaoProfessor alocacao;
        try {
            // flush ja aqui: duas alocacoes simultaneas passam pelo exists acima,
            // e a segunda so e barrada pela UNIQUE do banco.
            alocacao = alocacaoRepository.saveAndFlush(AlocacaoProfessor.builder()
                    .escolaId(escolaId)
                    .professor(professor)
                    .turma(turma)
                    .disciplina(disciplina)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new AlocacaoJaExisteException();
        }
        return AlocacaoProfessorResponse.de(alocacao, buscarPessoa(professor, escolaId));
    }

    @Transactional
    public void desalocar(UUID professorId, UUID alocacaoId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        AlocacaoProfessor alocacao = alocacaoRepository
                .findByIdAndEscolaIdAndProfessorId(alocacaoId, escolaId, professorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alocacao nao encontrada"));
        alocacaoRepository.delete(alocacao);
    }

    private Professor buscarProfessor(UUID id, UUID escolaId) {
        return professorRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor nao encontrado"));
    }

    private Pessoa buscarPessoa(Professor professor, UUID escolaId) {
        return pessoaRepository.findByIdAndEscolaId(professor.getPessoaId(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor nao encontrado"));
    }

    private Turma buscarTurma(Long id, UUID escolaId) {
        return turmaRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma nao encontrada"));
    }
}
