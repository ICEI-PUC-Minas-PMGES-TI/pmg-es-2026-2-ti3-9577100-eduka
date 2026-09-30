package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DisciplinaResponse {
    private Long id;
    private UUID escolaId;
    private Long cursoId;
    private String cursoNome;
    private String nome;
    private String codigo;
    private Integer cargaHoraria;
    private String descricao;
    private Boolean ativa;
}

