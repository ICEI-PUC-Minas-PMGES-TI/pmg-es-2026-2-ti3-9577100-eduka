package br.pucminas.eduka.backend.dto.academico;

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
    /** Periodo planejado so recebe matricula com isto verdadeiro (IU09). */
    private boolean matriculasAbertas;
    /** Preenchido quando a secretaria encerrou o periodo (IU09). */
    private LocalDate encerradoEm;
}
