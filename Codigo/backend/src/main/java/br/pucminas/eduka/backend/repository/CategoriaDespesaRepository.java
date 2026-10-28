package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.CategoriaDespesa;

@Repository
public interface CategoriaDespesaRepository extends JpaRepository<CategoriaDespesa, UUID> {

    List<CategoriaDespesa> findAllByEscolaIdOrderByNome(UUID escolaId);

    Optional<CategoriaDespesa> findByIdAndEscolaId(UUID id, UUID escolaId);

    boolean existsByEscolaIdAndNomeIgnoreCase(UUID escolaId, String nome);
}
