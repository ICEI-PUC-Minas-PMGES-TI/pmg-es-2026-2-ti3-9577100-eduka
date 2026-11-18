package br.pucminas.eduka.backend.repository.academico;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;

@Repository
public interface AlocacaoProfessorRepository extends JpaRepository<AlocacaoProfessor, UUID> {

    List<AlocacaoProfessor> findAllByEscolaIdAndProfessorId(UUID escolaId, UUID professorId);

    /** Mesma consulta, ja trazendo turma (com periodo e curso) e disciplina numa query so. */
    @EntityGraph(attributePaths = { "turma", "turma.periodoLetivo", "turma.curso", "disciplina" })
    List<AlocacaoProfessor> findAllComTurmaEDisciplinaByEscolaIdAndProfessorId(UUID escolaId, UUID professorId);

    List<AlocacaoProfessor> findAllByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    /** Quem leciona cada disciplina de varias turmas, ja com o professor (portal do aluno). */
    @EntityGraph(attributePaths = { "professor" })
    List<AlocacaoProfessor> findAllComProfessorByEscolaIdAndTurmaIdIn(UUID escolaId, Collection<Long> turmaIds);

    /** A alocacao da escola, ja com a disciplina; turma e professor sao so referencias. */
    @EntityGraph(attributePaths = { "disciplina" })
    Optional<AlocacaoProfessor> findByIdAndEscolaId(UUID id, UUID escolaId);

    Optional<AlocacaoProfessor> findByIdAndEscolaIdAndProfessorId(UUID id, UUID escolaId, UUID professorId);

    boolean existsByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId);

    boolean existsByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    /** O professor leciona esta disciplina nesta turma? */
    boolean existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(UUID escolaId, UUID professorId, Long turmaId,
            Long disciplinaId);
}
