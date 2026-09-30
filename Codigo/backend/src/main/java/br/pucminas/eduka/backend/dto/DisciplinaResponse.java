package br.pucminas.eduka.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DisciplinaResponse {
    private Long id;
    private Long escolaId;
    private Long cursoId;
    private String cursoNome;
    private String nome;
    private String codigo;
    private Integer cargaHoraria;
    private String descricao;
    private Boolean ativa;
}

