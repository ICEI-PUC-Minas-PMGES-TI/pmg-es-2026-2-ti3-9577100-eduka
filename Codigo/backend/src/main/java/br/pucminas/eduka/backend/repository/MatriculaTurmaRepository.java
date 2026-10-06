package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.StatusMatricula;

@Repository
public interface MatriculaTurmaRepository extends JpaRepository<MatriculaTurma, UUID> {

    // Os EntityGraph trazem aluno e turma (com periodo e curso) numa query so,
    // para montar a resposta sem N+1.

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    List<MatriculaTurma> findAllByEscolaId(UUID escolaId);

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    List<MatriculaTurma> findAllByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    List<MatriculaTurma> findAllByEscolaIdAndAlunoId(UUID escolaId, UUID alunoId);

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    Optional<MatriculaTurma> findByIdAndEscolaId(UUID id, UUID escolaId);

    boolean existsByEscolaIdAndAlunoIdAndTurmaId(UUID escolaId, UUID alunoId, Long turmaId);

    /** Vagas ocupadas: so matricula ATIVA conta. */
    long countByEscolaIdAndTurmaIdAndStatus(UUID escolaId, Long turmaId, StatusMatricula status);
}
