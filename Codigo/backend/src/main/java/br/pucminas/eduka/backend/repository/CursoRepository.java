package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.Curso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    List<Curso> findAllByEscolaId(UUID escolaId);

    long countByEscolaId(UUID escolaId);
    Optional<Curso> findByIdAndEscolaId(Long id, UUID escolaId);
}

