package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CursoResponse {
    private Long id;
    private UUID escolaId;
    private String nome;
    private String codigo;
    private String descricao;
    private Boolean ativo;
}
