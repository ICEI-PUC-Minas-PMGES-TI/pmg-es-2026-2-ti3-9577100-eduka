package br.pucminas.eduka.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;

@Repository
public interface ContaRecebimentoEscolaRepository extends JpaRepository<ContaRecebimentoEscola, UUID> {

    /** Conta da escola; vazio enquanto o MASTER nao ativou os recebimentos. */
    Optional<ContaRecebimentoEscola> findByEscolaId(UUID escolaId);
}
