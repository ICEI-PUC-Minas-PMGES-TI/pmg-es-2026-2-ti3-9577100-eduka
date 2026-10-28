package br.pucminas.eduka.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.ClienteProvedor;
import br.pucminas.eduka.backend.domain.ContaProvedorCliente;

@Repository
public interface ClienteProvedorRepository extends JpaRepository<ClienteProvedor, UUID> {

    Optional<ClienteProvedor> findByEscolaIdAndContaAndReferenciaExterna(UUID escolaId, ContaProvedorCliente conta,
            String referenciaExterna);
}
