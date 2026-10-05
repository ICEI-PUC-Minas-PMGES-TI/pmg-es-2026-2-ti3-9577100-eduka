package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.AlocacaoProfessorRequest;
import br.pucminas.eduka.backend.dto.AlocacaoProfessorResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.AlocacaoProfessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Alocacao de professores nas disciplinas das turmas da escola do usuario
 * autenticado. A alocacao e um sub-recurso do professor; a visao por turma
 * e so leitura.
 *
 * <p>
 * Gestao e secretaria montam a grade de professores, como no cadastro de
 * professores.
 * </p>
 */
@RestController
@PreAuthorize(Perfis.CADASTRO_ACADEMICO)
@Tag(name = "Alocacao de professores",
        description = "Professores nas disciplinas das turmas. Perfis: MASTER, SECRETARIA.")
public class AlocacaoProfessorController {

    private final AlocacaoProfessorService alocacaoService;

    public AlocacaoProfessorController(AlocacaoProfessorService alocacaoService) {
        this.alocacaoService = alocacaoService;
    }

    @GetMapping("/professores/{professorId}/alocacoes")
    @Operation(summary = "Lista as alocacoes de um professor")
    public List<AlocacaoProfessorResponse> listarPorProfessor(@PathVariable UUID professorId) {
        return alocacaoService.listarPorProfessor(professorId);
    }

    @PostMapping("/professores/{professorId}/alocacoes")
    @Operation(summary = "Aloca um professor em uma disciplina de uma turma")
    public ResponseEntity<AlocacaoProfessorResponse> alocar(@PathVariable UUID professorId,
            @Valid @RequestBody AlocacaoProfessorRequest request) {
        AlocacaoProfessorResponse response = alocacaoService.alocar(professorId, request);
        return ResponseEntity.created(URI.create("/professores/" + professorId + "/alocacoes/" + response.id()))
                .body(response);
    }

    @DeleteMapping("/professores/{professorId}/alocacoes/{alocacaoId}")
    @Operation(summary = "Remove uma alocacao do professor")
    public ResponseEntity<Void> desalocar(@PathVariable UUID professorId, @PathVariable UUID alocacaoId) {
        alocacaoService.desalocar(professorId, alocacaoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/turmas/{turmaId}/alocacoes")
    @Operation(summary = "Lista os professores alocados em uma turma")
    public List<AlocacaoProfessorResponse> listarPorTurma(@PathVariable Long turmaId) {
        return alocacaoService.listarPorTurma(turmaId);
    }
}
