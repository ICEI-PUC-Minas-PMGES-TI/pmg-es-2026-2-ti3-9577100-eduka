package br.pucminas.eduka.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.PessoaPapel;

public interface PessoaPapelRepository extends JpaRepository<PessoaPapel, UUID> {
}
