package br.pucminas.eduka.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.DashboardEscolaResponse;
import br.pucminas.eduka.backend.dto.DashboardSecretariaResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Metricas dos dashboards da gestao e da secretaria (RF20). A escola vem do
 * token. O do professor fica no portal dele ({@link PortalProfessorController}).
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboards", description = "Metricas por perfil da escola do usuario autenticado.")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/escola")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Resumo da escola", description = "Pessoas, estrutura academica, ocupacao de vagas e aproveitamento geral. Perfil: MASTER.")
    public DashboardEscolaResponse escola() {
        return dashboardService.escola();
    }

    @GetMapping("/secretaria")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Resumo de matriculas", description = "Matriculas por situacao, vagas e ocupacao das turmas ativas. Perfis: MASTER, SECRETARIA.")
    public DashboardSecretariaResponse secretaria() {
        return dashboardService.secretaria();
    }
}
