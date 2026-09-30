package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PeriodoLetivoRepository extends JpaRepository<PeriodoLetivo, Long> {
    List<PeriodoLetivo> findAllByEscolaId(Long escolaId);
    Optional<PeriodoLetivo> findByIdAndEscolaId(Long id, Long escolaId);
}
