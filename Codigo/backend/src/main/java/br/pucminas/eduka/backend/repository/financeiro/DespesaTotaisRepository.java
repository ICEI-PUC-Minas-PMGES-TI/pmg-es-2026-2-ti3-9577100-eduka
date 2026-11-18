package br.pucminas.eduka.backend.repository.financeiro;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import br.pucminas.eduka.backend.domain.financeiro.Despesa;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;

/** Fragmento de {@link DespesaRepository} com a agregacao da listagem paginada. */
public interface DespesaTotaisRepository {

    /**
     * Quantidade e soma dos valores das despesas que passam em {@code filtro},
     * agrupadas pela situacao. Situacao sem despesa nao aparece.
     */
    List<Grupo> agruparPorSituacao(Specification<Despesa> filtro);

    record Grupo(SituacaoDespesa situacao, long quantidade, BigDecimal valor) {
    }
}
