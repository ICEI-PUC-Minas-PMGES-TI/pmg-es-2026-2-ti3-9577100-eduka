package br.pucminas.eduka.backend.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.EventoProvedor;
import br.pucminas.eduka.backend.domain.StatusEventoProvedor;
import jakarta.persistence.LockModeType;

@Repository
public interface EventoProvedorRepository extends JpaRepository<EventoProvedor, UUID> {

    boolean existsByIdEventoExterno(String idEventoExterno);

    /**
     * Avisos que ainda nao terminaram, com tentativas sobrando, recebidos antes
     * de {@code recebidoAntes}; os mais antigos primeiro (indice
     * {@code evento_pendente_idx}, V27).
     */
    @Query("SELECT e.id FROM EventoProvedor e WHERE e.status IN :status AND e.tentativas < :maxTentativas "
            + "AND e.recebidoEm < :recebidoAntes ORDER BY e.recebidoEm")
    List<UUID> buscarPendentes(@Param("status") Collection<StatusEventoProvedor> status,
            @Param("maxTentativas") int maxTentativas, @Param("recebidoAntes") Instant recebidoAntes,
            Pageable pagina);

    /** Trava o evento enquanto ele e aplicado: o webhook e o agendador nao o aplicam ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EventoProvedor e WHERE e.id = :id")
    Optional<EventoProvedor> travar(@Param("id") UUID id);
}
