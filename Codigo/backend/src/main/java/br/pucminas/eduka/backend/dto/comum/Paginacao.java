package br.pucminas.eduka.backend.dto.comum;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Pagina pedida a uma listagem: {@code pagina} comeca em 0 e {@code tamanho}
 * vai de 1 a {@link #TAMANHO_MAXIMO}. Mesmos limites em todas as listagens
 * paginadas, para a tela usar um componente so.
 *
 * @throws IllegalArgumentException (400) se a pagina ou o tamanho estao fora
 *                                  dos limites
 */
public record Paginacao(int pagina, int tamanho) {

    public static final int TAMANHO_MAXIMO = 100;

    /** Valor padrao do parametro {@code tamanho} nos controllers. */
    public static final String TAMANHO_PADRAO = "20";

    /**
     * Ordem alfabetica de nomes em memoria (os nomes de pessoa sao cifrados e
     * nao ordenam no banco): sem diferenciar maiusculas nem acentos, "Álvaro"
     * junto de "Alvaro".
     */
    public static final Comparator<String> ORDEM_DE_NOME = Comparator.nullsLast(collator());

    public Paginacao {
        if (pagina < 0) {
            throw new IllegalArgumentException("Pagina deve ser zero ou maior");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Tamanho deve ser de 1 a " + TAMANHO_MAXIMO);
        }
    }

    public PageRequest comOrdem(Sort ordem) {
        return PageRequest.of(pagina, tamanho, ordem);
    }

    private static Comparator<String> collator() {
        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        collator.setStrength(Collator.PRIMARY);
        return collator::compare;
    }
}
