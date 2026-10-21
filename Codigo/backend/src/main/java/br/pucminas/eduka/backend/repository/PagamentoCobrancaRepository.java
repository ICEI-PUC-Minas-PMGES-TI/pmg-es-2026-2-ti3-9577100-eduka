package br.pucminas.eduka.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.PagamentoCobranca;

@Repository
public interface PagamentoCobrancaRepository extends JpaRepository<PagamentoCobranca, UUID> {

    boolean existsByCobrancaId(UUID cobrancaId);
}
