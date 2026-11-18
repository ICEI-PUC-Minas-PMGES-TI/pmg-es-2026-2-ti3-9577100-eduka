package br.pucminas.eduka.backend.repository.financeiro;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.financeiro.ClienteProvedor;
import br.pucminas.eduka.backend.domain.financeiro.ContaProvedorCliente;

@Repository
public interface ClienteProvedorRepository extends JpaRepository<ClienteProvedor, UUID> {

    Optional<ClienteProvedor> findByEscolaIdAndContaAndReferenciaExterna(UUID escolaId, ContaProvedorCliente conta,
            String referenciaExterna);
}
