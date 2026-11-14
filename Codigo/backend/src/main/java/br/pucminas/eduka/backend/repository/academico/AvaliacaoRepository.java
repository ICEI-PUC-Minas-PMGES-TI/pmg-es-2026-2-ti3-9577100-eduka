package br.pucminas.eduka.backend.repository.academico;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.academico.Avaliacao;

@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

    @EntityGraph(attributePaths = { "turma", "disciplina" })
    List<Avaliacao> findAllByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId);

    /** Todas as avaliacoes da escola (dashboards). */
    @EntityGraph(attributePaths = { "turma", "disciplina" })
    long countByEscolaId(UUID escolaId);

    /** Avaliacoes de varias turmas de uma vez (portal do aluno). */
    @EntityGraph(attributePaths = { "turma", "disciplina" })
    List<Avaliacao> findAllByEscolaIdAndTurmaIdIn(UUID escolaId, Collection<Long> turmaIds);

    boolean existsByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    boolean existsByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId);

    @EntityGraph(attributePaths = { "turma", "disciplina" })
    Optional<Avaliacao> findByIdAndEscolaId(UUID id, UUID escolaId);
}
