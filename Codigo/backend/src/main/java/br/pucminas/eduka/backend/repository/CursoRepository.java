package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.Curso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    List<Curso> findAllByEscolaId(Long escolaId);
    Optional<Curso> findByIdAndEscolaId(Long id, Long escolaId);
}

