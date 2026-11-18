package br.pucminas.eduka.backend.controller.academico;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.academico.AtualizarHorarioTurmaRequest;
import br.pucminas.eduka.backend.dto.academico.HorarioTurmaRequest;
import br.pucminas.eduka.backend.dto.academico.HorarioTurmaResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.academico.HorarioTurmaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Grade semanal (horario) das turmas da escola do usuario autenticado. A aula
 * e um sub-recurso da turma e nasce de uma alocacao dela.
 *
 * <p>
 * Gestao e secretaria montam e consultam a grade, como na alocacao de
 * professores.
 * </p>
 */
@RestController
@PreAuthorize(Perfis.CADASTRO_ACADEMICO)
@Tag(name = "Horario das turmas",
        description = "Grade semanal das turmas (dia, horario, disciplina e professor). Perfis: MASTER, SECRETARIA.")
public class HorarioTurmaController {

    private final HorarioTurmaService horarioService;

    public HorarioTurmaController(HorarioTurmaService horarioService) {
        this.horarioService = horarioService;
    }

    @GetMapping("/turmas/{turmaId}/horario")
    @Operation(summary = "Grade semanal da turma, ordenada por dia e hora")
    public List<HorarioTurmaResponse> listar(@PathVariable Long turmaId) {
        return horarioService.listar(turmaId);
    }

    @PostMapping("/turmas/{turmaId}/horario")
    @Operation(summary = "Acrescenta uma aula na grade, a partir de uma alocacao da turma",
            description = "409 com codigo HORARIO_TURMA_CONFLITANTE ou HORARIO_PROFESSOR_CONFLITANTE se o horario sobrepoe outra aula.")
    public ResponseEntity<HorarioTurmaResponse> criar(@PathVariable Long turmaId,
            @Valid @RequestBody HorarioTurmaRequest request) {
        HorarioTurmaResponse response = horarioService.criar(turmaId, request);
        return ResponseEntity.created(URI.create("/turmas/" + turmaId + "/horario/" + response.id()))
                .body(response);
    }

    @PutMapping("/turmas/{turmaId}/horario/{horarioId}")
    @Operation(summary = "Muda o dia e o horario de uma aula da grade",
            description = "409 com codigo HORARIO_TURMA_CONFLITANTE ou HORARIO_PROFESSOR_CONFLITANTE se o novo horario sobrepoe outra aula.")
    public HorarioTurmaResponse atualizar(@PathVariable Long turmaId, @PathVariable UUID horarioId,
            @Valid @RequestBody AtualizarHorarioTurmaRequest request) {
        return horarioService.atualizar(turmaId, horarioId, request);
    }

    @DeleteMapping("/turmas/{turmaId}/horario/{horarioId}")
    @Operation(summary = "Remove uma aula da grade")
    public ResponseEntity<Void> remover(@PathVariable Long turmaId, @PathVariable UUID horarioId) {
        horarioService.remover(turmaId, horarioId);
        return ResponseEntity.noContent().build();
    }
}
