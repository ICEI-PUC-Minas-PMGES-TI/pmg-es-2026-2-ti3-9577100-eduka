package br.pucminas.eduka.backend.dto.financeiro;

/** Se a escola pode receber pagamentos e, se nao, por que (R11). */
public record HabilitacaoRecebimentos(boolean habilitados, String motivo) {
}
