package br.pucminas.eduka.backend.repository.financeiro;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.financeiro.EstornoCobranca;

@Repository
public interface EstornoCobrancaRepository extends JpaRepository<EstornoCobranca, UUID> {

    /**
     * Estornos de {@code desde} (inclusive) ate {@code antes} (exclusive), com o
     * valor negativo: quem soma os recebimentos do periodo soma estes junto e
     * chega ao que ficou. {@code pagaEm} e o dia do estorno.
     */
    @Query("SELECT e.estornadoEm AS pagaEm, -e.valor AS valor, p.formaPagamento AS forma, c.descricao AS descricao, "
            + "c.aluno.pessoaId AS pessoaId FROM EstornoCobranca e "
            + "JOIN Cobranca c ON c.id = e.cobrancaId AND c.escolaId = e.escolaId "
            + "JOIN PagamentoCobranca p ON p.cobrancaId = e.cobrancaId AND p.escolaId = e.escolaId "
            + "WHERE e.escolaId = :escolaId AND e.estornadoEm >= :desde AND e.estornadoEm < :antes "
            + "ORDER BY e.estornadoEm, e.id")
    List<CobrancaRepository.Recebimento> estornosEntre(@Param("escolaId") UUID escolaId,
            @Param("desde") Instant desde, @Param("antes") Instant antes);
}
