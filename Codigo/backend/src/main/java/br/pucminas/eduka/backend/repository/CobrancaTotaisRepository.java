package br.pucminas.eduka.backend.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.StatusCobranca;

/** Fragmento de {@link CobrancaRepository} com a agregacao da listagem paginada. */
public interface CobrancaTotaisRepository {

    /**
     * Quantidade e soma dos valores das cobrancas que passam em
     * {@code filtro}, agrupadas pela situacao gravada e pelo vencimento. O
     * vencimento entra no grupo para que o servico chegue a situacao efetiva
     * ({@link StatusCobranca#efetivo}) sem repetir o "hoje" no GROUP BY.
     */
    List<Grupo> agruparPorStatusEVencimento(Specification<Cobranca> filtro);

    /** Quantos alunos diferentes tem cobranca que passa em {@code filtro}. */
    long contarAlunos(Specification<Cobranca> filtro);

    record Grupo(StatusCobranca status, LocalDate dataVencimento, long quantidade, BigDecimal valor) {
    }
}
