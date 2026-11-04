package br.pucminas.eduka.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Aula;

@Repository
public interface AulaRepository extends JpaRepository<Aula, UUID> {

    /** Pagina das aulas da disciplina na turma, ja com turma e disciplina. */
    @EntityGraph(attributePaths = { "turma", "disciplina" })
    Page<Aula> findAllByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId,
            Pageable pagina);

    @EntityGraph(attributePaths = { "turma", "disciplina" })
    Optional<Aula> findByIdAndEscolaId(UUID id, UUID escolaId);

    /** A turma tem aula registrada (a FK da V35 impede apagar a turma). */
    boolean existsByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    /** A disciplina tem aula registrada na turma (a FK da V35 impede tira-la da grade). */
    boolean existsByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId);
}
