package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.Turma;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TurmaRepository extends JpaRepository<Turma, Long> {
    List<Turma> findAllByEscolaId(Long escolaId);
    Optional<Turma> findByIdAndEscolaId(Long id, Long escolaId);
}
