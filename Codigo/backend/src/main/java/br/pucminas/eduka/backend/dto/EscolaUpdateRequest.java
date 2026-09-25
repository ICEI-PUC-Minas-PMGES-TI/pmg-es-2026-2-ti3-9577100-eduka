package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.Set;

import br.pucminas.eduka.backend.domain.ModuloEscola;
import br.pucminas.eduka.backend.domain.PlanoEscola;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Edicao de escola (IU03). Campo nulo mantem o valor atual; {@code endereco}
 * e {@code modulos}, quando informados, substituem os anteriores por inteiro.
 *
 * <p>
 * CNPJ, e-mail e telefone podem ser omitidos, mas nao apagados (enviados em
 * branco), e o endereco, quando enviado, precisa vir completo — as mesmas
 * regras do cadastro.
 * </p>
 *
 * <p>
 * Nao ha {@code inicioVigencia}: o inicio e fixado no cadastro e nao muda. Se
 * vier no corpo, e ignorado.
 * </p>
 */
public record EscolaUpdateRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres") String nome,
        @Pattern(regexp = NAO_EM_BRANCO, message = "cnpjCpf não pode ficar em branco")
        @Size(max = 20, message = "cnpjCpf deve ter no máximo 20 caracteres") String cnpjCpf,
        @Valid EnderecoDto endereco,
        @Pattern(regexp = NAO_EM_BRANCO, message = "email não pode ficar em branco")
        @Email(message = "email deve ser um endereço válido")
        @Size(max = 150, message = "email deve ter no máximo 150 caracteres") String email,
        @Pattern(regexp = NAO_EM_BRANCO, message = "telefone não pode ficar em branco")
        @Size(max = 20, message = "telefone deve ter no máximo 20 caracteres") String telefone,
        PlanoEscola plano,
        Boolean ativo,
        LocalDate fimVigencia,
        @Positive(message = "limiteUsuarios deve ser maior que zero") Integer limiteUsuarios,
        Set<ModuloEscola> modulos) {

    /** Aceita nulo (mantem o valor atual), mas rejeita texto vazio ou so com espacos. */
    private static final String NAO_EM_BRANCO = "(?s).*\\S.*";
}
