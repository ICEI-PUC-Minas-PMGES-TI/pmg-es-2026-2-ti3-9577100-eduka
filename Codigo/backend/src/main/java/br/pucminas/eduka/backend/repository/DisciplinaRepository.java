package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisciplinaRepository extends JpaRepository<Disciplina, Long> {
    List<Disciplina> findAllByEscolaId(UUID escolaId);
    Optional<Disciplina> findByIdAndEscolaId(Long id, UUID escolaId);

    boolean existsByEscolaIdAndCursoId(UUID escolaId, Long cursoId);
    List<Disciplina> findAllByIdInAndEscolaId(List<Long> ids, UUID escolaId);
}
