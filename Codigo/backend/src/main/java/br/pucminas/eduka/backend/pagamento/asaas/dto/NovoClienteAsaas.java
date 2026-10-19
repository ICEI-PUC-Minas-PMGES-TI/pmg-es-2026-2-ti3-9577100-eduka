package br.pucminas.eduka.backend.pagamento.asaas.dto;

/**
 * Corpo de {@code POST /v3/customers}. {@code notificationDisabled} desliga os
 * e-mails e SMS do Asaas: quem avisa o pagador e o Eduka.
 */
public record NovoClienteAsaas(
        String name,
        String cpfCnpj,
        String email,
        String externalReference,
        boolean notificationDisabled) {

    @Override
    public String toString() {
        return "NovoClienteAsaas[externalReference=%s]".formatted(externalReference);
    }
}
