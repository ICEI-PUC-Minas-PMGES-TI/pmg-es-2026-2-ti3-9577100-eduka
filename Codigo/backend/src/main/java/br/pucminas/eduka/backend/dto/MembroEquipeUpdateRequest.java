package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.validation.Cpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edicao de alguem da equipe. O perfil nao muda: quem passa de secretaria para
 * financeiro e inativado e cadastrado de novo, para o log de quem fez o que
 * continuar fiel. Trocar o e-mail troca o login junto.
 */
public record MembroEquipeUpdateRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @NotBlank(message = "cpf é obrigatório")
        @Cpf String cpf,
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone) {
}
