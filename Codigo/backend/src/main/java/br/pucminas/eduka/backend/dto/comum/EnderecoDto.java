package br.pucminas.eduka.backend.dto.comum;

import br.pucminas.eduka.backend.domain.comum.Endereco;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Endereco da escola na API. Quando enviado (cadastro ou edicao), todos os
 * campos sao obrigatorios. Na resposta pode vir incompleto ou nulo: escolas
 * antigas foram cadastradas antes dessa regra.
 */
public record EnderecoDto(
        @NotBlank(message = "logradouro é obrigatório")
        @Size(max = 200, message = "logradouro deve ter no máximo 200 caracteres") String logradouro,
        @NotBlank(message = "numero é obrigatório")
        @Size(max = 10, message = "numero deve ter no máximo 10 caracteres") String numero,
        @NotBlank(message = "bairro é obrigatório")
        @Size(max = 100, message = "bairro deve ter no máximo 100 caracteres") String bairro,
        @NotBlank(message = "cidade é obrigatória")
        @Size(max = 100, message = "cidade deve ter no máximo 100 caracteres") String cidade,
        @NotBlank(message = "estado é obrigatório")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "estado deve ser a sigla da UF") String estado,
        @NotBlank(message = "cep é obrigatório")
        @Pattern(regexp = "^\\d{8}$", message = "cep deve ter 8 dígitos") String cep) {

    public Endereco paraDominio() {
        return new Endereco(logradouro, numero, bairro, cidade, estado, cep);
    }

    public static EnderecoDto from(Endereco endereco) {
        if (endereco == null) {
            return null;
        }
        return new EnderecoDto(endereco.logradouro(), endereco.numero(), endereco.bairro(), endereco.cidade(),
                endereco.estado(), endereco.cep());
    }
}
