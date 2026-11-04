package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.HorarioAula;

public interface HorarioAulaRepository extends JpaRepository<HorarioAula, UUID> {

    List<HorarioAula> findAllByEscolaIdAndTurmaIdOrderByDiaSemanaAscHoraInicioAsc(UUID escolaId, Long turmaId);

    List<HorarioAula> findAllByEscolaIdAndTurmaIdInOrderByDiaSemanaAscHoraInicioAsc(UUID escolaId,
            Collection<Long> turmaIds);

    void deleteAllByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId);
}
