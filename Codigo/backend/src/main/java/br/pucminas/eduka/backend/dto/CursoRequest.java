package br.pucminas.eduka.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CursoRequest {
    @NotBlank(message = "O nome do curso é obrigatório")
    private String nome;

    private String codigo;
    private String descricao;
}

