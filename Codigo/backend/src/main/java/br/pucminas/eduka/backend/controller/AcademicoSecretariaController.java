package br.pucminas.eduka.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.AcademicoSecretariaService;

@RestController
@RequestMapping("/academico/secretaria")
public class AcademicoSecretariaController {

    private final AcademicoSecretariaService academicoSecretariaService;

    public AcademicoSecretariaController(AcademicoSecretariaService academicoSecretariaService) {
        this.academicoSecretariaService = academicoSecretariaService;
    }

    @GetMapping("/periodos-letivos")
    public ResponseEntity<?> listarPeriodosLetivos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID escolaId = usuario.escolaId(); 
        return ResponseEntity.ok(academicoSecretariaService.listarPeriodosLetivos(escolaId));
    }

    @GetMapping("/cursos")
    public ResponseEntity<?> listarCursos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID escolaId = usuario.escolaId();
        return ResponseEntity.ok(academicoSecretariaService.listarCursos(escolaId));
    }

    @GetMapping("/disciplinas")
    public ResponseEntity<?> listarDisciplinas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID escolaId = usuario.escolaId();
        return ResponseEntity.ok(academicoSecretariaService.listarDisciplinas(escolaId));
    }

    @GetMapping("/turmas")
    public ResponseEntity<?> listarTurmas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID escolaId = usuario.escolaId();
        return ResponseEntity.ok(academicoSecretariaService.listarTurmas(escolaId));
    }

}
