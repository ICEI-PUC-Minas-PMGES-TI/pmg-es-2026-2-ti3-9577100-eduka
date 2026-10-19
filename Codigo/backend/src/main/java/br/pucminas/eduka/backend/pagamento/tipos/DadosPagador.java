package br.pucminas.eduka.backend.pagamento.tipos;

/** Quem paga, cadastrado como cliente na conta do provedor. */
public record DadosPagador(String referenciaExterna, String nome, String cpfCnpj, String email) {

    /** CPF/CNPJ e e-mail nunca vao para log. */
    @Override
    public String toString() {
        return "DadosPagador[referenciaExterna=%s]".formatted(referenciaExterna);
    }
}
