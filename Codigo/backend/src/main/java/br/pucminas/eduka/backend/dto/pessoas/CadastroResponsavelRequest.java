package br.pucminas.eduka.backend.dto.pessoas;

import br.pucminas.eduka.backend.dto.comum.EnderecoRequest;
import br.pucminas.eduka.backend.validation.Cpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro e edicao de responsavel em {@code /responsaveis}. Na edicao
 * substitui o registro inteiro: campo opcional enviado nulo apaga o valor
 * anterior.
 *
 * <p>
 * CPF e obrigatorio porque o responsavel financeiro e o sacado das
 * cobrancas. Nao existe campo {@code escolaId} de proposito: a escola vem
 * sempre do token. O vinculo com alunos e feito em
 * {@code /alunos/{alunoId}/responsaveis}.
 * </p>
 */
public record CadastroResponsavelRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @NotBlank(message = "cpf é obrigatório")
        @Cpf String cpf,
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone,
        @Valid EnderecoRequest endereco) {
}
