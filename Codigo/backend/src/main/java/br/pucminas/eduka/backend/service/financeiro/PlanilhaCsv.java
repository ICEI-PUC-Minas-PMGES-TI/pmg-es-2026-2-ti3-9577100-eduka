package br.pucminas.eduka.backend.service.financeiro;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

import br.pucminas.eduka.backend.dto.comum.ArquivoCsv.Separador;

/**
 * Monta um CSV com uma ou mais secoes, cada uma com titulo, cabecalho e linhas.
 *
 * <p>
 * Feito para abrir direto no Excel em portugues: UTF-8 com BOM (sem ele, os
 * acentos quebram), datas dd/mm/aaaa e, com ponto e virgula, a virgula decimal.
 * Com o separador virgula, o decimal e o ponto, para o valor nao virar duas
 * colunas.
 * </p>
 *
 * <p>
 * Texto vindo do usuario que comece com {@code = + - @}, tabulacao ou quebra de
 * linha ganha um apostrofo na frente: sem isso, a planilha o executaria como
 * formula (CSV injection).
 * </p>
 */
final class PlanilhaCsv {

    private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FIM_DE_LINHA = "\r\n";

    private final char separador;
    private final StringBuilder texto = new StringBuilder();

    PlanilhaCsv(Separador separador) {
        this.separador = separador.caractere();
    }

    /** Comeca uma secao; da segunda em diante, separada por uma linha em branco. */
    void secao(String titulo, String... colunas) {
        if (!texto.isEmpty()) {
            texto.append(FIM_DE_LINHA);
        }
        linha(titulo);
        linha((Object[]) colunas);
    }

    /** Aceita texto, {@link BigDecimal}, {@link LocalDate} e nulo (celula vazia). */
    void linha(Object... celulas) {
        texto.append(Arrays.stream(celulas).map(this::celula).collect(Collectors.joining(String.valueOf(separador))))
                .append(FIM_DE_LINHA);
    }

    byte[] paraBytes() {
        byte[] corpo = texto.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream arquivo = new ByteArrayOutputStream(BOM.length + corpo.length);
        arquivo.writeBytes(BOM);
        arquivo.writeBytes(corpo);
        return arquivo.toByteArray();
    }

    private String celula(Object valor) {
        return switch (valor) {
            case null -> "";
            case BigDecimal numero -> separador == ';' ? numero.toPlainString().replace('.', ',')
                    : numero.toPlainString();
            case LocalDate data -> data.format(DATA);
            default -> texto(valor.toString());
        };
    }

    private String texto(String valor) {
        String seguro = pareceFormula(valor) ? "'" + valor : valor;
        boolean precisaDeAspas = seguro.indexOf(separador) >= 0 || seguro.indexOf('"') >= 0
                || seguro.indexOf('\n') >= 0 || seguro.indexOf('\r') >= 0;
        return precisaDeAspas ? '"' + seguro.replace("\"", "\"\"") + '"' : seguro;
    }

    private static boolean pareceFormula(String valor) {
        return !valor.isEmpty() && "=+-@\t\r\n".indexOf(valor.charAt(0)) >= 0;
    }
}
