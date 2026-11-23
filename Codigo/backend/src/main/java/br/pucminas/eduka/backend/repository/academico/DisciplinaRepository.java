package br.pucminas.eduka.backend.repository.academico;

import br.pucminas.eduka.backend.domain.academico.Disciplina;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisciplinaRepository extends JpaRepository<Disciplina, Long> {
    List<Disciplina> findAllByEscolaId(UUID escolaId);

    long countByEscolaId(UUID escolaId);
    Optional<Disciplina> findByIdAndEscolaId(Long id, UUID escolaId);

    boolean existsByEscolaIdAndCursoId(UUID escolaId, Long cursoId);
    List<Disciplina> findAllByIdInAndEscolaId(List<Long> ids, UUID escolaId);
}
