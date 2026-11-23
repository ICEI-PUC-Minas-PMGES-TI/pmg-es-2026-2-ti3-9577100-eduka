package br.pucminas.eduka.backend.repository.academico;

import br.pucminas.eduka.backend.domain.academico.Curso;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    List<Curso> findAllByEscolaId(UUID escolaId);

    long countByEscolaId(UUID escolaId);
    Optional<Curso> findByIdAndEscolaId(Long id, UUID escolaId);
}

