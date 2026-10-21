package br.pucminas.eduka.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.EventoProvedor;

@Repository
public interface EventoProvedorRepository extends JpaRepository<EventoProvedor, UUID> {
}
