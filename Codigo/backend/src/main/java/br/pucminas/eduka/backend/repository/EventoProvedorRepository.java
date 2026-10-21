package br.pucminas.eduka.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.EventoProvedor;
import jakarta.persistence.LockModeType;

@Repository
public interface EventoProvedorRepository extends JpaRepository<EventoProvedor, UUID> {

    boolean existsByIdEventoExterno(String idEventoExterno);

    /** Trava o evento enquanto ele e aplicado: o webhook e o agendador nao o aplicam ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EventoProvedor e WHERE e.id = :id")
    Optional<EventoProvedor> travar(@Param("id") UUID id);
}
