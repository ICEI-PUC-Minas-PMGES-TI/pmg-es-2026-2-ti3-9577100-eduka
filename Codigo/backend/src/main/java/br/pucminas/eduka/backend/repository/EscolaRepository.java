package br.pucminas.eduka.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domains.model.Escola;

@Repository
public interface EscolaRepository extends JpaRepository<Escola, UUID> {

    boolean existsByCnpj(String cnpj);
}
