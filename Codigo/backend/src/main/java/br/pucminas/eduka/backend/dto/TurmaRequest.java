package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domain.Turno;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Set;

@Data
public class TurmaRequest {
    @NotNull(message = "O período letivo é obrigatório")
    private Long periodoLetivoId;

    @NotNull(message = "O curso é obrigatório")
    private Long cursoId;

    @NotBlank(message = "O nome da turma é obrigatório")
    private String nome;

    private String codigo;

    @NotNull(message = "A quantidade de vagas é obrigatória")
    @Min(value = 0, message = "As vagas não podem ser negativas")
    private Integer vagas;

    @NotNull(message = "O turno é obrigatório")
    private Turno turno;

    private Set<Long> disciplinaIds;
}
