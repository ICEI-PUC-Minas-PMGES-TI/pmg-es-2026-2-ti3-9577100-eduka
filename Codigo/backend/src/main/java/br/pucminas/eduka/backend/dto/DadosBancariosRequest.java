package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.pagamento.tipos.TipoContaBancaria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Conta bancaria da escola, enviada na ativacao dos recebimentos. Nunca e
 * gravada: so o codigo do banco e os 4 ultimos digitos da conta ficam.
 */
public record DadosBancariosRequest(
        @NotBlank(message = "codigoBanco é obrigatório")
        @Pattern(regexp = "\\d{3}", message = "codigoBanco deve ter 3 dígitos") String codigoBanco,
        @NotBlank(message = "agencia é obrigatória")
        @Pattern(regexp = "\\d{1,5}", message = "agencia deve ter até 5 dígitos") String agencia,
        @NotBlank(message = "conta é obrigatória")
        @Pattern(regexp = "\\d{1,12}", message = "conta deve ter até 12 dígitos") String conta,
        @NotBlank(message = "digitoConta é obrigatório")
        @Pattern(regexp = "[0-9Xx]{1,2}", message = "digitoConta inválido") String digitoConta,
        @NotNull(message = "tipo é obrigatório") TipoContaBancaria tipo) {

    /** So o codigo do banco aparece; agencia, conta e digito nunca vao para log. */
    @Override
    public String toString() {
        return "DadosBancariosRequest[codigoBanco=%s, agencia=***, conta=***, digitoConta=***, tipo=%s]"
                .formatted(codigoBanco, tipo);
    }
}
