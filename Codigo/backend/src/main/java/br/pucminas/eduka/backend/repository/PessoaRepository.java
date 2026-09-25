package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.Pessoa;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {

    Optional<Pessoa> findByIdAndEscolaId(UUID id, UUID escolaId);

    List<Pessoa> findAllByIdInAndEscolaId(Collection<UUID> ids, UUID escolaId);
}
