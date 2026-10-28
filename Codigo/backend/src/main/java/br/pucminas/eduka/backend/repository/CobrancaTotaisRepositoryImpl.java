package br.pucminas.eduka.backend.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

class CobrancaTotaisRepositoryImpl implements CobrancaTotaisRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Grupo> agruparPorStatusEVencimento(Specification<Cobranca> filtro) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Cobranca> cobranca = query.from(Cobranca.class);
        Path<StatusCobranca> status = cobranca.get("status");
        Path<LocalDate> vencimento = cobranca.get("dataVencimento");
        Expression<Long> quantidade = cb.count(cobranca);
        Expression<BigDecimal> valor = cb.sum(cobranca.<BigDecimal>get("valor"));

        query.select(cb.tuple(status, vencimento, quantidade, valor))
                .where(filtro.toPredicate(cobranca, query, cb))
                .groupBy(status, vencimento);
        return entityManager.createQuery(query).getResultStream()
                .map(linha -> new Grupo(linha.get(status), linha.get(vencimento), linha.get(quantidade),
                        linha.get(valor)))
                .toList();
    }
}
