package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Nota;

@Repository
public interface NotaRepository extends JpaRepository<Nota, UUID> {

    @EntityGraph(attributePaths = { "aluno" })
    List<Nota> findAllByEscolaIdAndAvaliacaoId(UUID escolaId, UUID avaliacaoId);

    boolean existsByEscolaIdAndAvaliacaoId(UUID escolaId, UUID avaliacaoId);

    /** Quantas notas ja foram lancadas em cada avaliacao, numa query so. */
    @Query("""
            select n.avaliacao.id as avaliacaoId, count(n) as total
            from Nota n
            where n.escolaId = :escolaId and n.avaliacao.id in :avaliacaoIds
            group by n.avaliacao.id
            """)
    List<NotasPorAvaliacao> contarPorAvaliacao(@Param("escolaId") UUID escolaId,
            @Param("avaliacaoIds") Collection<UUID> avaliacaoIds);

    interface NotasPorAvaliacao {
        UUID getAvaliacaoId();

        long getTotal();
    }
}
