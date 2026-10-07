package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.Turno;
import lombok.Builder;
import lombok.Data;
import java.util.Set;

@Data
@Builder
public class TurmaResponse {
    private Long id;
    private UUID escolaId;
    private Long periodoLetivoId;
    private String periodoLetivoNome;
    private Long cursoId;
    private String cursoNome;
    private String nome;
    private String codigo;
    private Integer vagas;
    private Turno turno;
    private Boolean ativa;
    private Set<DisciplinaResponse> disciplinas;
}

