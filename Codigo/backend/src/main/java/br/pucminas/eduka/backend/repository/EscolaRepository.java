package br.pucminas.eduka.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.Escola;

public interface EscolaRepository extends JpaRepository<Escola, UUID> {

    boolean existsByCnpjCpf(String cnpjCpf);

    boolean existsByCnpjCpfAndIdNot(String cnpjCpf, UUID id);
}
