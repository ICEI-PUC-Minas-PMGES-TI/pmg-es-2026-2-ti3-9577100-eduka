package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edicao de secretaria. Login e senha nao entram aqui: nao sao editaveis por
 * este endpoint (a troca de senha e um fluxo separado, ja existente em
 * {@link br.pucminas.eduka.backend.service.UsuarioService}).
 */
public record SecretariaUpdateRequest(
        @NotBlank(message = "nome é obrigatório") @Size(max = 150) String nome,
        @NotBlank(message = "cpf é obrigatório") @Pattern(regexp = "^\\d{11}$", message = "cpf deve ter 11 dígitos") String cpf,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String telefone) {
}
