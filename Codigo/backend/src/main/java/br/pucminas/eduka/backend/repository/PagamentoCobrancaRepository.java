package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.PagamentoCobranca;
import jakarta.persistence.LockModeType;

@Repository
public interface PagamentoCobrancaRepository extends JpaRepository<PagamentoCobranca, UUID> {

    Optional<PagamentoCobranca> findByCobrancaId(UUID cobrancaId);

    /** Sem {@code @TenantId} na entidade: a escola entra explicita no filtro. */
    Optional<PagamentoCobranca> findByEscolaIdAndCobrancaId(UUID escolaId, UUID cobrancaId);

    /** Sem {@code @TenantId} na entidade: a escola entra explicita no filtro. */
    List<PagamentoCobranca> findAllByEscolaIdAndCobrancaIdIn(UUID escolaId, Collection<UUID> cobrancaIds);

    /** Trava o pagamento enquanto um estorno e aplicado: dois avisos ao mesmo tempo nao descontam em dobro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PagamentoCobranca p WHERE p.escolaId = :escolaId AND p.cobrancaId = :cobrancaId")
    Optional<PagamentoCobranca> travar(@Param("escolaId") UUID escolaId, @Param("cobrancaId") UUID cobrancaId);
}
