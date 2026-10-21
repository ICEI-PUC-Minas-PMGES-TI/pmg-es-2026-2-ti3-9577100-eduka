package br.pucminas.eduka.backend.pagamento.asaas.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo de {@code POST /v3/accounts}. Nao leva conta bancaria: a criacao da
 * subconta nao tem campo para conta externa (achado A16 do spike).
 * {@code webhooks} fica fora do JSON quando nulo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NovaContaAsaas(
        String name,
        String email,
        String cpfCnpj,
        String mobilePhone,
        BigDecimal incomeValue,
        String companyType,
        String address,
        String addressNumber,
        String complement,
        String province,
        String postalCode,
        List<WebhookAsaas> webhooks) {

    @Override
    public String toString() {
        return "NovaContaAsaas[companyType=%s]".formatted(companyType);
    }
}
