package br.pucminas.eduka.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Passa para EM_ATRASO as pendentes com vencimento anterior a {@code hoje},
     * de todas as escolas, e devolve quantas mudaram. SQL nativo de proposito:
     * a rotina roda sem usuario autenticado, e em JPQL o {@code @TenantId}
     * filtraria por uma escola que nao existe.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE COBRANCA SET STATUS = 'EM_ATRASO', ATUALIZADO_EM = :agora "
            + "WHERE STATUS = 'PENDENTE' AND DATA_VENCIMENTO < :hoje", nativeQuery = true)
    int marcarVencidasComoEmAtraso(@Param("hoje") LocalDate hoje, @Param("agora") Instant agora);
}
