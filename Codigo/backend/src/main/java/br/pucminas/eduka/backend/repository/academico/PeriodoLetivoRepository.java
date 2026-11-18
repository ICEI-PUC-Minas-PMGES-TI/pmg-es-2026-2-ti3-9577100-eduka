package br.pucminas.eduka.backend.repository.academico;

import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodoLetivoRepository extends JpaRepository<PeriodoLetivo, Long> {
    List<PeriodoLetivo> findAllByEscolaId(UUID escolaId);
    Optional<PeriodoLetivo> findByIdAndEscolaId(Long id, UUID escolaId);
}
