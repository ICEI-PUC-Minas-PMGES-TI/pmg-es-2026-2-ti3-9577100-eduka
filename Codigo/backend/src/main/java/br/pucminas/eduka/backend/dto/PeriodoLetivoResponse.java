// Exemplo para PeriodoLetivoResponse / CursoResponse / DisciplinaResponse / TurmaResponse
package br.pucminas.eduka.backend.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeriodoLetivoResponse {
    private UUID id;
    private UUID escolaId; // Garantir que é UUID e não Long
    private String nome;
}
