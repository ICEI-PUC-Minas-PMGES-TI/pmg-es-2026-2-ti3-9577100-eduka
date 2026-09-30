package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeriodoLetivoResponse {
    private Long id;
    private UUID escolaId;
    private String nome;
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private Boolean ativo;
}
