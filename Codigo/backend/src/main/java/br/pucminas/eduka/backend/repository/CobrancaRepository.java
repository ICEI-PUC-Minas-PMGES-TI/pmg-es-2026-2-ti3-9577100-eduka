package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Cobranca;

@Repository
public interface CobrancaRepository extends JpaRepository<Cobranca, UUID> {

    // Os EntityGraph trazem aluno e matricula (com a turma) numa query so,
    // para montar a resposta sem N+1.

    @EntityGraph(attributePaths = { "aluno", "matricula", "matricula.turma" })
    List<Cobranca> findAllByEscolaId(UUID escolaId);

    @EntityGraph(attributePaths = { "aluno", "matricula", "matricula.turma" })
    List<Cobranca> findAllByEscolaIdAndAlunoId(UUID escolaId, UUID alunoId);

    @EntityGraph(attributePaths = { "aluno", "matricula", "matricula.turma" })
    Optional<Cobranca> findByIdAndEscolaId(UUID id, UUID escolaId);
}
