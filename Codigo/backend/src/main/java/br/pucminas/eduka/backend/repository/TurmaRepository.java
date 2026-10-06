package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.Turma;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurmaRepository extends JpaRepository<Turma, Long> {
    List<Turma> findAllByEscolaId(UUID escolaId);
    Optional<Turma> findByIdAndEscolaId(Long id, UUID escolaId);

    /**
     * Mesma busca, travando a linha da turma ate o fim da transacao. Usada ao
     * ocupar vaga: duas matriculas simultaneas na mesma turma passam uma de
     * cada vez pela contagem de vagas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turma t where t.id = :id and t.escola.id = :escolaId")
    Optional<Turma> findComLockByIdAndEscolaId(@Param("id") Long id, @Param("escolaId") UUID escolaId);
}
