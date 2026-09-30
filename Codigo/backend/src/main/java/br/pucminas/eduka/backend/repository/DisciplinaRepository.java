package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DisciplinaRepository extends JpaRepository<Disciplina, Long> {
    List<Disciplina> findAllByEscolaId(Long escolaId);
    Optional<Disciplina> findByIdAndEscolaId(Long id, Long escolaId);
    List<Disciplina> findAllByIdInAndEscolaId(List<Long> ids, Long escolaId);
}
