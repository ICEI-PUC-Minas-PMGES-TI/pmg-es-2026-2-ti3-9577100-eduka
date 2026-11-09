package br.pucminas.eduka.backend.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Despesa;

@Repository
public interface DespesaRepository extends JpaRepository<Despesa, UUID>, JpaSpecificationExecutor<Despesa> {

    // Os EntityGraph trazem a categoria na mesma query, para montar a resposta sem N+1.

    /** Listagem com os filtros da tela, feitos no banco. */
    @Override
    @EntityGraph(attributePaths = "categoria")
    List<Despesa> findAll(Specification<Despesa> filtro, Sort sort);

    /** Listagem paginada; a contagem da pagina vem em consulta propria, sem o join. */
    @Override
    @EntityGraph(attributePaths = "categoria")
    Page<Despesa> findAll(Specification<Despesa> filtro, Pageable pagina);

    @EntityGraph(attributePaths = "categoria")
    Optional<Despesa> findByIdAndEscolaId(UUID id, UUID escolaId);

    // ---- Painel financeiro: contas no banco, sem carregar as despesas ----

    /** Soma (nula se nenhuma) e quantidade das A_PAGAR que vencem ate {@code ate}. */
    @Query("SELECT SUM(d.valor) AS valor, COUNT(d) AS quantidade FROM Despesa d WHERE d.escolaId = :escolaId "
            + "AND d.situacao = br.pucminas.eduka.backend.domain.SituacaoDespesa.A_PAGAR AND d.dataVencimento <= :ate")
    Soma somarAPagarAte(@Param("escolaId") UUID escolaId, @Param("ate") LocalDate ate);

    /** Vencimento mais proximo, a partir de {@code desde}, entre as A_PAGAR. */
    @Query("SELECT MIN(d.dataVencimento) FROM Despesa d WHERE d.escolaId = :escolaId "
            + "AND d.situacao = br.pucminas.eduka.backend.domain.SituacaoDespesa.A_PAGAR "
            + "AND d.dataVencimento >= :desde")
    Optional<LocalDate> proximoVencimentoAPagar(@Param("escolaId") UUID escolaId, @Param("desde") LocalDate desde);

    /** Pagas de {@code desde} (inclusive) ate {@code antes} (exclusive). */
    @Query("SELECT d.pagaEm AS pagaEm, d.valor AS valor FROM Despesa d WHERE d.escolaId = :escolaId "
            + "AND d.situacao = br.pucminas.eduka.backend.domain.SituacaoDespesa.PAGA "
            + "AND d.pagaEm >= :desde AND d.pagaEm < :antes")
    List<Movimento> pagasEntre(@Param("escolaId") UUID escolaId, @Param("desde") Instant desde,
            @Param("antes") Instant antes);

    // ---- Relatorios financeiros ----

    /** Soma e quantidade das nao canceladas que vencem de {@code de} a {@code ate} (inclusive), por vencimento. */
    @Query("SELECT d.dataVencimento AS vencimento, SUM(d.valor) AS valor, COUNT(d) AS quantidade FROM Despesa d "
            + "WHERE d.escolaId = :escolaId "
            + "AND d.situacao <> br.pucminas.eduka.backend.domain.SituacaoDespesa.CANCELADA "
            + "AND d.dataVencimento BETWEEN :de AND :ate GROUP BY d.dataVencimento")
    List<SomaDoVencimento> lancadasPorVencimento(@Param("escolaId") UUID escolaId, @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);

    /** As despesas de {@link #lancadasPorVencimento}, uma a uma, para a exportacao. */
    @EntityGraph(attributePaths = "categoria")
    @Query("SELECT d FROM Despesa d WHERE d.escolaId = :escolaId "
            + "AND d.situacao <> br.pucminas.eduka.backend.domain.SituacaoDespesa.CANCELADA "
            + "AND d.dataVencimento BETWEEN :de AND :ate ORDER BY d.dataVencimento, d.id")
    List<Despesa> lancadasEntre(@Param("escolaId") UUID escolaId, @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);

    interface Soma {
        BigDecimal getValor();

        long getQuantidade();
    }

    interface SomaDoVencimento extends Soma {
        LocalDate getVencimento();
    }
}
