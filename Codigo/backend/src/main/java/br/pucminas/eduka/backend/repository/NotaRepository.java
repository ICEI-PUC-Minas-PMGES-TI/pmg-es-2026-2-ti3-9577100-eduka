package br.pucminas.eduka.backend.repository;

import java.math.BigDecimal;
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

    /** Todas as notas de um aluno (portal do aluno). */
    @EntityGraph(attributePaths = { "avaliacao" })
    List<Nota> findAllByEscolaIdAndAlunoId(UUID escolaId, UUID alunoId);

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

    /** Quantidade e soma das notas de cada avaliacao (aproveitamento nos dashboards). */
    @Query("""
            select n.avaliacao.id as avaliacaoId, count(n) as total, sum(n.valor) as soma
            from Nota n
            where n.escolaId = :escolaId
            group by n.avaliacao.id
            """)
    List<SomaPorAvaliacao> somarPorAvaliacao(@Param("escolaId") UUID escolaId);

    /** Quem ja tem nota em cada avaliacao, sem carregar as entidades (notas pendentes). */
    @Query("""
            select n.avaliacao.id as avaliacaoId, n.aluno.id as alunoId
            from Nota n
            where n.escolaId = :escolaId and n.avaliacao.id in :avaliacaoIds
            """)
    List<NotaLancada> listarLancadas(@Param("escolaId") UUID escolaId,
            @Param("avaliacaoIds") Collection<UUID> avaliacaoIds);

    interface SomaPorAvaliacao {
        UUID getAvaliacaoId();

        long getTotal();

        BigDecimal getSoma();
    }

    interface NotaLancada {
        UUID getAvaliacaoId();

        UUID getAlunoId();
    }

    interface NotasPorAvaliacao {
        UUID getAvaliacaoId();

        long getTotal();
    }
}
