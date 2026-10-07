package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.PeriodoLetivo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeriodoLetivoRepository extends JpaRepository<PeriodoLetivo, Long> {
    List<PeriodoLetivo> findAllByEscolaId(UUID escolaId);
    Optional<PeriodoLetivo> findByIdAndEscolaId(Long id, UUID escolaId);
}
