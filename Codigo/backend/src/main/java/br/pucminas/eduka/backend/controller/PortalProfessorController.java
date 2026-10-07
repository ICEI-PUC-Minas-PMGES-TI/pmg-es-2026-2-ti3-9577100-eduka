package br.pucminas.eduka.backend.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.DashboardProfessorResponse;
import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.DashboardService;
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
    private final DashboardService dashboardService;

    public PortalProfessorController(PortalProfessorService portalProfessorService,
            DashboardService dashboardService) {
        this.portalProfessorService = portalProfessorService;
        this.dashboardService = dashboardService;
    }

    /** Turmas em que o professor leciona, cada uma com as disciplinas dele. */
    @GetMapping("/turmas")
    @Operation(summary = "Lista as turmas do professor", description = "Cada turma vem com as disciplinas que ele leciona nela.")
    public List<TurmaDoProfessorResponse> listarMinhasTurmas() {
        return portalProfessorService.listarMinhasTurmas();
    }

    /** Resumo das turmas, alunos, proximas avaliacoes e notas pendentes do professor. */
    @GetMapping("/dashboard")
    @Operation(summary = "Resumo do professor", description = "Turmas, alunos ativos, avaliacoes dos proximos 30 dias e avaliacoes ja realizadas com nota faltando.")
    public DashboardProfessorResponse dashboard() {
        return dashboardService.professor();
    }
}
