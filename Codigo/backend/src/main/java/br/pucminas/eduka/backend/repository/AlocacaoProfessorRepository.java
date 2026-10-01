package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;

@Repository
public interface AlocacaoProfessorRepository extends JpaRepository<AlocacaoProfessor, UUID> {

    List<AlocacaoProfessor> findAllByEscolaIdAndProfessorId(UUID escolaId, UUID professorId);

    List<AlocacaoProfessor> findAllByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    Optional<AlocacaoProfessor> findByIdAndEscolaIdAndProfessorId(UUID id, UUID escolaId, UUID professorId);

    boolean existsByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId);
}
