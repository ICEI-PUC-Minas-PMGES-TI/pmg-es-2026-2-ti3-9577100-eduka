package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domain.Endereco;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Endereco da escola na API. Todos os campos sao opcionais. */
public record EnderecoDto(
        @Size(max = 200, message = "logradouro deve ter no máximo 200 caracteres") String logradouro,
        @Size(max = 10, message = "numero deve ter no máximo 10 caracteres") String numero,
        @Size(max = 100, message = "bairro deve ter no máximo 100 caracteres") String bairro,
        @Size(max = 100, message = "cidade deve ter no máximo 100 caracteres") String cidade,
        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "estado deve ser a sigla da UF") String estado,
        @Pattern(regexp = "^$|^\\d{8}$", message = "cep deve ter 8 dígitos") String cep) {

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
