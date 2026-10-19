package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;

import br.pucminas.eduka.backend.pagamento.tipos.TipoEmpresa;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cadastro da conta de recebimento da escola (MASTER). Sem CNPJ: ele vem do
 * cadastro da escola do token.
 */
public record AtivarRecebimentosRequest(
        @NotBlank(message = "razaoSocial é obrigatória")
        @Size(min = 3, max = 150, message = "razaoSocial deve ter entre 3 e 150 caracteres") String razaoSocial,
        @NotBlank(message = "emailFinanceiro é obrigatório")
        @Email(message = "emailFinanceiro inválido")
        @Size(max = 150, message = "emailFinanceiro deve ter no máximo 150 caracteres") String emailFinanceiro,
        @NotBlank(message = "telefoneCelular é obrigatório")
        @Pattern(regexp = "\\d{10,11}", message = "telefoneCelular deve ter 10 ou 11 dígitos") String telefoneCelular,
        @NotNull(message = "tipoEmpresa é obrigatório") TipoEmpresa tipoEmpresa,
        @NotNull(message = "faturamentoMensal é obrigatório")
        @Positive(message = "faturamentoMensal deve ser maior que zero")
        @Digits(integer = 12, fraction = 2, message = "faturamentoMensal deve ter até 12 dígitos inteiros e 2 decimais") BigDecimal faturamentoMensal,
        @NotBlank(message = "logradouro é obrigatório")
        @Size(max = 200, message = "logradouro deve ter no máximo 200 caracteres") String logradouro,
        @NotBlank(message = "numero é obrigatório")
        @Size(max = 10, message = "numero deve ter no máximo 10 caracteres") String numero,
        @Size(max = 100, message = "complemento deve ter no máximo 100 caracteres") String complemento,
        @NotBlank(message = "bairro é obrigatório")
        @Size(max = 100, message = "bairro deve ter no máximo 100 caracteres") String bairro,
        @NotBlank(message = "cep é obrigatório")
        @Pattern(regexp = "\\d{8}", message = "cep deve ter 8 dígitos") String cep,
        @NotNull(message = "dadosBancarios é obrigatório") @Valid DadosBancariosRequest dadosBancarios) {
}
