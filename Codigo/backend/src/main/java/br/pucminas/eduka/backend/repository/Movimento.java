package br.pucminas.eduka.backend.repository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dinheiro que entrou ou saiu e quando: so as duas colunas, para o painel
 * somar por mes sem carregar as entidades.
 */
public interface Movimento {

    Instant getPagaEm();

    BigDecimal getValor();
}
