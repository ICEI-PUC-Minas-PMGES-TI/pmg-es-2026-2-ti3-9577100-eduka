package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Responsavel;

@Repository
public interface ResponsavelRepository extends JpaRepository<Responsavel, UUID> {

    List<Responsavel> findAllByEscolaId(UUID escolaId);

    Optional<Responsavel> findByIdAndEscolaId(UUID id, UUID escolaId);

    List<Responsavel> findAllByPessoaIdInAndEscolaId(Collection<UUID> pessoaIds, UUID escolaId);
}
