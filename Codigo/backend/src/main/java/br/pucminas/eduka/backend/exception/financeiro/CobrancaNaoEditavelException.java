package br.pucminas.eduka.backend.exception.financeiro;

/**
 * Alguem ja clicou em Pagar e a cobranca existe (ou pode existir) no provedor:
 * mudar valor ou vencimento aqui desencontraria os dois lados (R24).
 */
public class CobrancaNaoEditavelException extends RuntimeException {

    public CobrancaNaoEditavelException() {
        super("O pagamento desta cobranca ja foi gerado; nao da mais para editar. Cancele e lance outra");
    }
}
