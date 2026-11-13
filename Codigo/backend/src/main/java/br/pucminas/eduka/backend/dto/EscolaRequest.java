package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domain.PlanoEscola;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de cadastro de escola. {@code id}, {@code criadoEm} e {@code versao}
 * nao entram: sao geridos pelo banco/Hibernate.
 *
 * <p>
 * Todos os dados sao obrigatorios, inclusive o endereco completo. Apenas
 * {@code ativo} e opcional: omitido (nulo) vira {@code true}.
 * </p>
 *
 * <p>
 * {@code master} e o gestor da escola: o cadastro ja cria o login MASTER dele
 * e manda o e-mail de primeiro acesso.
 * </p>
 */
public record EscolaRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @NotBlank(message = "cnpjCpf é obrigatório")
        @Size(max = 20, message = "cnpjCpf deve ter no máximo 20 caracteres") String cnpjCpf,
        @NotNull(message = "endereco é obrigatório") @Valid EnderecoDto endereco,
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @NotBlank(message = "telefone é obrigatório")
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone,
        @NotNull(message = "plano é obrigatório") PlanoEscola plano,
        Boolean ativo,
        @NotNull(message = "master é obrigatório") @Valid MasterInicialRequest master) {
}
