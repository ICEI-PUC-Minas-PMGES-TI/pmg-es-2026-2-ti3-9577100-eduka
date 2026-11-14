package br.pucminas.eduka.backend.repository.financeiro;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import br.pucminas.eduka.backend.domain.financeiro.Despesa;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoDespesa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

class DespesaTotaisRepositoryImpl implements DespesaTotaisRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Grupo> agruparPorSituacao(Specification<Despesa> filtro) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Despesa> despesa = query.from(Despesa.class);
        Path<SituacaoDespesa> situacao = despesa.get("situacao");
        Expression<Long> quantidade = cb.count(despesa);
        Expression<BigDecimal> valor = cb.sum(despesa.<BigDecimal>get("valor"));

        query.select(cb.tuple(situacao, quantidade, valor))
                .where(filtro.toPredicate(despesa, query, cb))
                .groupBy(situacao);
        return entityManager.createQuery(query).getResultStream()
                .map(linha -> new Grupo(linha.get(situacao), linha.get(quantidade), linha.get(valor)))
                .toList();
    }
}
