package br.pucminas.eduka.backend.pagamento.tipos;

/**
 * Conta bancaria informada pela escola na ativacao. Nunca persistida: do
 * banco so ficam o codigo e os 4 ultimos digitos da conta.
 */
public record DadosBancarios(
        String codigoBanco,
        String agencia,
        String conta,
        String digitoConta,
        TipoContaBancaria tipo) {

    /** So o codigo do banco aparece; agencia, conta e digito nunca vao para log. */
    @Override
    public String toString() {
        return "DadosBancarios[codigoBanco=%s, agencia=***, conta=***, digitoConta=***, tipo=%s]"
                .formatted(codigoBanco, tipo);
    }
}
