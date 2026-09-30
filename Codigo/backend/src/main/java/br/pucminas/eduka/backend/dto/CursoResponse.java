package br.pucminas.eduka.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CursoResponse {
    private Long id;
    private Long escolaId;
    private String nome;
    private String codigo;
    private String descricao;
    private Boolean ativo;
}
