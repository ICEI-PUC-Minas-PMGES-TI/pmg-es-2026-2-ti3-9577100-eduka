package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.Set;

import br.pucminas.eduka.backend.domain.ModuloEscola;
import br.pucminas.eduka.backend.domain.PlanoEscola;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Edicao de escola (IU03). Campo nulo mantem o valor atual; {@code endereco}
 * e {@code modulos}, quando informados, substituem os anteriores por inteiro.
 *
 * <p>
 * Nao ha {@code inicioVigencia}: o inicio e fixado no cadastro e nao muda. Se
 * vier no corpo, e ignorado.
 * </p>
 */
public record EscolaUpdateRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @Size(max = 20, message = "cnpjCpf deve ter no máximo 20 caracteres") String cnpjCpf,
        @Valid EnderecoDto endereco,
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone,
        PlanoEscola plano,
        Boolean ativo,
        LocalDate fimVigencia,
        @Positive(message = "limiteUsuarios deve ser maior que zero") Integer limiteUsuarios,
        Set<ModuloEscola> modulos) {
}
