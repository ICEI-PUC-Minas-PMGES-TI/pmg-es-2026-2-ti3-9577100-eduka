package br.pucminas.eduka.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;

@Repository
public interface ContaRecebimentoEscolaRepository extends JpaRepository<ContaRecebimentoEscola, UUID> {

    /** Conta da escola; vazio enquanto o MASTER nao ativou os recebimentos. */
    Optional<ContaRecebimentoEscola> findByEscolaId(UUID escolaId);

    /**
     * Escola dona da conta no provedor. Nativo: o webhook chega sem usuario
     * autenticado, e o {@code @TenantId} filtraria por uma escola que nao existe.
     */
    @Query(value = "SELECT ESCOLA_ID FROM CONTA_RECEBIMENTO_ESCOLA WHERE ID_CONTA_EXTERNA = :idContaExterna",
            nativeQuery = true)
    Optional<UUID> buscarEscolaPorIdContaExterna(@Param("idContaExterna") String idContaExterna);
}
