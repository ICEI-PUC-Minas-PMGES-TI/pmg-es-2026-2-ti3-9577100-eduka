package br.pucminas.eduka.backend.service.privacidade;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Como os dados aparecem para gente, no padrao brasileiro (documentos para
 * imprimir ou entregar ao titular). Valor nulo vira nulo: quem desenha decide
 * o que mostrar no lugar.
 */
final class Formatos {

    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Formatos() {
    }

    static String data(LocalDate data) {
        return data == null ? null : DATA.format(data);
    }

    static String data(Instant instante) {
        return instante == null ? null : DATA.format(instante.atZone(FUSO_ESCOLA));
    }

    /** "08/11/2026 às 14:32", no horario da escola. */
    static String dataHora(Instant instante) {
        if (instante == null) {
            return null;
        }
        var local = instante.atZone(FUSO_ESCOLA);
        return DATA.format(local) + " às " + HORA.format(local);
    }

    /** "R$ 1.234,56", com espaco comum (o NumberFormat usa o nao separavel). */
    static String moeda(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        return NumberFormat.getCurrencyInstance(BRASIL).format(valor).replace(' ', ' ');
    }

    /** "8,5": sem zeros sobrando a direita. */
    static String numero(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        NumberFormat formato = NumberFormat.getNumberInstance(BRASIL);
        formato.setMaximumFractionDigits(2);
        return formato.format(valor);
    }

    static String cpf(String cpf) {
        String digitos = digitos(cpf);
        if (digitos == null || digitos.length() != 11) {
            return cpf;
        }
        return digitos.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }

    /** CPF ou CNPJ, pelo numero de digitos. */
    static String documento(String documento) {
        String digitos = digitos(documento);
        if (digitos != null && digitos.length() == 14) {
            return digitos.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        return cpf(documento);
    }

    static String cep(String cep) {
        String digitos = digitos(cep);
        if (digitos == null || digitos.length() != 8) {
            return cep;
        }
        return digitos.substring(0, 5) + "-" + digitos.substring(5);
    }

    static String telefone(String telefone) {
        String digitos = digitos(telefone);
        if (digitos == null) {
            return telefone;
        }
        return switch (digitos.length()) {
            case 11 -> digitos.replaceFirst("(\\d{2})(\\d{5})(\\d{4})", "($1) $2-$3");
            case 10 -> digitos.replaceFirst("(\\d{2})(\\d{4})(\\d{4})", "($1) $2-$3");
            default -> telefone;
        };
    }

    private static String digitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }
}
