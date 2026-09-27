package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Professor;

@Repository
public interface ProfessorRepository extends JpaRepository<Professor, UUID> {

    List<Professor> findAllByEscolaId(UUID escolaId);

    Optional<Professor> findByIdAndEscolaId(UUID id, UUID escolaId);
}
