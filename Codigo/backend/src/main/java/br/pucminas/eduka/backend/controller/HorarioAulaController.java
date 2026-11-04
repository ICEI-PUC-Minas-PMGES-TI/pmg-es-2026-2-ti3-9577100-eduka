package br.pucminas.eduka.backend.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.DefinirHorariosRequest;
import br.pucminas.eduka.backend.dto.HorarioAulaResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.HorarioAulaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Horario das aulas de cada disciplina na turma (IU10 e IU13). */
@RestController
@Tag(name = "Horario das aulas", description = "Dias e horas das aulas de cada disciplina da turma.")
public class HorarioAulaController {

    private final HorarioAulaService horarioService;

    public HorarioAulaController(HorarioAulaService horarioService) {
        this.horarioService = horarioService;
    }

    @GetMapping("/turmas/{turmaId}/horarios")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Horario da turma", description = "Perfil: MASTER ou SECRETARIA. Por dia (1 = segunda) e hora.")
    public List<HorarioAulaResponse> listar(@PathVariable Long turmaId) {
        return horarioService.listarDaTurma(turmaId);
    }

    @PutMapping("/turmas/{turmaId}/disciplinas/{disciplinaId}/horarios")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Define o horario da disciplina na turma", description = "Perfil: MASTER ou SECRETARIA. "
            + "Substitui os horarios da disciplina e devolve os da turma toda.")
    @ApiResponse(responseCode = "200", description = "O horario da turma toda, ja com a mudanca.")
    @ApiResponse(responseCode = "400", description = "Disciplina fora da grade, horario repetido ou a turma ja tem "
            + "outra aula no mesmo dia e hora.")
    public List<HorarioAulaResponse> definir(@PathVariable Long turmaId, @PathVariable Long disciplinaId,
            @Valid @RequestBody DefinirHorariosRequest request) {
        return horarioService.definir(turmaId, disciplinaId, request);
    }
}
