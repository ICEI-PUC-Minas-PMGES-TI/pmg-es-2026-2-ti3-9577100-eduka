package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DisciplinaRequest {
    private Long cursoId;

    @NotBlank(message = "O nome da disciplina é obrigatório")
    private String nome;

    private String codigo;

    @NotNull(message = "A carga horária é obrigatória")
    @Min(value = 1, message = "A carga horária deve ser maior que zero")
    private Integer cargaHoraria;

    private String descricao;
}
