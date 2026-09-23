package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domain.PlanoEscola;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EscolaUpdateRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @Size(max = 20, message = "cnpjCpf deve ter no máximo 20 caracteres") String cnpjCpf,
        @Size(max = 200, message = "endereco deve ter no máximo 200 caracteres") String endereco,
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone,
        PlanoEscola plano) {
}
