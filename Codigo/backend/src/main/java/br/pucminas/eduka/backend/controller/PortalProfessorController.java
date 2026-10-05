package br.pucminas.eduka.backend.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.PortalProfessorService;

/**
 * Area pessoal do professor autenticado. Nao ha {@code professorId} nem
 * {@code escolaId} na URL: os dois saem do token (ver
 * {@link PortalProfessorService}).
 */
@RestController
@RequestMapping("/portal/professor")
@PreAuthorize(Perfis.PROFESSOR)
public class PortalProfessorController {

    private final PortalProfessorService portalProfessorService;

    public PortalProfessorController(PortalProfessorService portalProfessorService) {
        this.portalProfessorService = portalProfessorService;
    }

    /** Turmas em que o professor leciona, cada uma com as disciplinas dele. */
    @GetMapping("/turmas")
    public List<TurmaDoProfessorResponse> listarMinhasTurmas() {
        return portalProfessorService.listarMinhasTurmas();
    }
}
