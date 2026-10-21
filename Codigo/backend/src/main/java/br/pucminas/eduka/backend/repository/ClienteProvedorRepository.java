package br.pucminas.eduka.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.ClienteProvedor;

@Repository
public interface ClienteProvedorRepository extends JpaRepository<ClienteProvedor, UUID> {
}
