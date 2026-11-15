package br.pucminas.eduka.backend.dto.comum;

/** Planilha .xlsx pronta para baixar. */
public record ArquivoPlanilha(String nome, byte[] conteudo) {
}
