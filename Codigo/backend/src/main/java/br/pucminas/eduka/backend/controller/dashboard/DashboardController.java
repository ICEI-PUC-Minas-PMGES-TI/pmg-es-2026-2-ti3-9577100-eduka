package br.pucminas.eduka.backend.controller.dashboard;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.dashboard.DashboardEscolaResponse;
import br.pucminas.eduka.backend.dto.dashboard.DashboardFinanceiroResponse;
import br.pucminas.eduka.backend.dto.dashboard.DashboardSecretariaResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.dashboard.DashboardService;
import br.pucminas.eduka.backend.service.financeiro.DashboardFinanceiroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final DashboardFinanceiroService dashboardFinanceiroService;

    public DashboardController(DashboardService dashboardService,
            DashboardFinanceiroService dashboardFinanceiroService) {
        this.dashboardService = dashboardService;
        this.dashboardFinanceiroService = dashboardFinanceiroService;
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

    @GetMapping("/financeiro")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Painel financeiro", description = "Recebido no mes, falta receber, alunos em atraso e "
            + "inadimplencia, despesas a pagar, entradas e saidas dos 12 ultimos meses, cobrancas que vencem na "
            + "semana, ultimos pagamentos e ultimas acoes do log de auditoria. Perfis: MASTER, FINANCEIRO.")
    public DashboardFinanceiroResponse financeiro() {
        return dashboardFinanceiroService.painel();
    }

    @GetMapping("/financeiro/movimento")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Entradas e saidas por mes num periodo",
            description = "O grafico do painel financeiro com o periodo escolhido no calendario: cobrancas "
                    + "recebidas e despesas pagas de `de` a `ate` (AAAA-MM-DD, inclusivos), um item por mes, do "
                    + "mais antigo ao mais recente. Os meses das pontas contam so os dias do periodo. No maximo 24 "
                    + "meses. Perfis: MASTER, FINANCEIRO.")
    @ApiResponse(responseCode = "200", description = "Um item por mes do periodo, mesmo os sem movimento.")
    @ApiResponse(responseCode = "400", description = "Periodo invertido, com mais de 24 meses ou data invalida.")
    public List<DashboardFinanceiroResponse.Mes> movimento(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate ate) {
        return dashboardFinanceiroService.movimento(de, ate);
    }
}
