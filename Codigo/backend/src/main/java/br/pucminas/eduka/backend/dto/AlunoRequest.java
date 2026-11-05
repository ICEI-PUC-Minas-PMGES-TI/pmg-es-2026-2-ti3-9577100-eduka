package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.List;

import br.pucminas.eduka.backend.validation.Cpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de cadastro de aluno (e, opcionalmente, seus responsaveis). Nao
 * existe campo {@code escolaId} de proposito: a escola vem sempre do token
 * do usuario autenticado.
 *
 * <p>
 * CPF e e-mail sao obrigatorios porque o cadastro cria o login do aluno: o
 * e-mail vira o login e recebe o primeiro acesso, o CPF e a senha provisoria.
 * </p>
 */
public record AlunoRequest(
        String nome,
        @NotBlank(message = "cpf é obrigatório")
        @Cpf String cpf,
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        LocalDate dataNascimento,
        EnderecoRequest endereco,
        String matricula,
        String genero,
        List<@Valid ResponsavelRequest> responsaveis) {
}
