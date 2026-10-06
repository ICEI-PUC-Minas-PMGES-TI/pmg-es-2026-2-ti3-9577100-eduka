package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Avaliacao;

@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

    @EntityGraph(attributePaths = { "turma", "disciplina" })
    List<Avaliacao> findAllByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId);

    @EntityGraph(attributePaths = { "turma", "disciplina" })
    Optional<Avaliacao> findByIdAndEscolaId(UUID id, UUID escolaId);
}
