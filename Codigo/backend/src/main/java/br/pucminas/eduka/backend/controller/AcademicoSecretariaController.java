package br.pucminas.eduka.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.AcademicoSecretariaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/academico/secretaria")
@Tag(name = "Estrutura academica", description = "Periodos letivos, cursos, disciplinas e turmas da escola.")
public class AcademicoSecretariaController {

    private final AcademicoSecretariaService academicoSecretariaService;

    public AcademicoSecretariaController(AcademicoSecretariaService academicoSecretariaService) {
        this.academicoSecretariaService = academicoSecretariaService;
    }

    @GetMapping("/periodos-letivos")
    @Operation(summary = "Lista os periodos letivos")
    public ResponseEntity<?> listarPeriodosLetivos(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID escolaId = usuario.escolaId(); 
        return ResponseEntity.ok(academicoSecretariaService.listarPeriodosLetivos(escolaId));
    }

    @GetMapping("/cursos")
    @Operation(summary = "Lista os cursos")
    public ResponseEntity<?> listarCursos(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID escolaId = usuario.escolaId();
        return ResponseEntity.ok(academicoSecretariaService.listarCursos(escolaId));
    }

    @GetMapping("/disciplinas")
    @Operation(summary = "Lista as disciplinas")
    public ResponseEntity<?> listarDisciplinas(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID escolaId = usuario.escolaId();
        return ResponseEntity.ok(academicoSecretariaService.listarDisciplinas(escolaId));
    }

    @GetMapping("/turmas")
    @Operation(summary = "Lista as turmas")
    public ResponseEntity<?> listarTurmas(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID escolaId = usuario.escolaId();
        return ResponseEntity.ok(academicoSecretariaService.listarTurmas(escolaId));
    }

}
