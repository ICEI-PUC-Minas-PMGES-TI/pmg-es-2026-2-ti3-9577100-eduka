package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.PagamentoCobranca;

@Repository
public interface PagamentoCobrancaRepository extends JpaRepository<PagamentoCobranca, UUID> {

    Optional<PagamentoCobranca> findByCobrancaId(UUID cobrancaId);

    /** Sem {@code @TenantId} na entidade: a escola entra explicita no filtro. */
    List<PagamentoCobranca> findAllByEscolaIdAndCobrancaIdIn(UUID escolaId, Collection<UUID> cobrancaIds);
}
