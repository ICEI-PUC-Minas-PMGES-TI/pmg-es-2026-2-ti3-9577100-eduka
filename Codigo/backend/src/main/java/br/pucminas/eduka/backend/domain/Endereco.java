package br.pucminas.eduka.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Endereco da escola. Todos os campos sao opcionais; com todos nulos o
 * Hibernate carrega o endereco da escola como {@code null}.
 *
 * <p>
 * {@code estado} e a sigla da UF e {@code cep} guarda so os digitos.
 * </p>
 */
@Embeddable
public record Endereco(
        @Column(name = "LOGRADOURO") String logradouro,
        @Column(name = "NUMERO") String numero,
        @Column(name = "BAIRRO") String bairro,
        @Column(name = "CIDADE") String cidade,
        @Column(name = "ESTADO") String estado,
        @Column(name = "CEP") String cep) {

    /** Troca textos em branco por nulo; devolve nulo se nao sobrar nenhum campo. */
    public static Endereco normalizado(String logradouro, String numero, String bairro, String cidade,
            String estado, String cep) {
        Endereco endereco = new Endereco(
                vazioParaNulo(logradouro),
                vazioParaNulo(numero),
                vazioParaNulo(bairro),
                vazioParaNulo(cidade),
                estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(),
                vazioParaNulo(cep));
        return endereco.vazio() ? null : endereco;
    }

    private boolean vazio() {
        return logradouro == null && numero == null && bairro == null && cidade == null && estado == null
                && cep == null;
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
