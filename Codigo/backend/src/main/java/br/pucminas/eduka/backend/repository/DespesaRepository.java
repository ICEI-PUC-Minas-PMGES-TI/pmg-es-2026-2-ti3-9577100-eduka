package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Despesa;

@Repository
public interface DespesaRepository extends JpaRepository<Despesa, UUID> {

    // Os EntityGraph trazem a categoria na mesma query, para montar a resposta sem N+1.

    @EntityGraph(attributePaths = "categoria")
    List<Despesa> findAllByEscolaId(UUID escolaId);

    @EntityGraph(attributePaths = "categoria")
    Optional<Despesa> findByIdAndEscolaId(UUID id, UUID escolaId);
}
