package br.pucminas.eduka.backend.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.PortalProfessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Area pessoal do professor autenticado. Nao ha {@code professorId} nem
 * {@code escolaId} na URL: os dois saem do token (ver
 * {@link PortalProfessorService}).
 */
@RestController
@RequestMapping("/portal/professor")
@PreAuthorize(Perfis.PROFESSOR)
@Tag(name = "Portal do professor", description = "Area pessoal do professor autenticado. Perfil: PROFESSOR.")
public class PortalProfessorController {

    private final PortalProfessorService portalProfessorService;

    public PortalProfessorController(PortalProfessorService portalProfessorService) {
        this.portalProfessorService = portalProfessorService;
    }

    /** Turmas em que o professor leciona, cada uma com as disciplinas dele. */
    @GetMapping("/turmas")
    @Operation(summary = "Lista as turmas do professor", description = "Cada turma vem com as disciplinas que ele leciona nela.")
    public List<TurmaDoProfessorResponse> listarMinhasTurmas() {
        return portalProfessorService.listarMinhasTurmas();
    }
}
