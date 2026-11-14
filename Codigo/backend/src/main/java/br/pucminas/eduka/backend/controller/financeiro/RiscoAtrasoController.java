package br.pucminas.eduka.backend.controller.financeiro;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.financeiro.NivelRiscoAtraso;
import br.pucminas.eduka.backend.dto.financeiro.RiscoAtrasoResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.financeiro.RiscoAtrasoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Risco de atraso dos alunos, com o motivo. Mesmo publico das cobrancas:
 * {@code /cobrancas} ja e so de gestao e financeiro; em {@code /alunos} a
 * rota-mae deixa entrar secretaria e professor, e o {@code @PreAuthorize}
 * restringe.
 */
@RestController
@PreAuthorize(Perfis.FINANCEIRO)
@Tag(name = "Cobrancas", description = "Cobrancas dos alunos. Todas as operacoes: MASTER, FINANCEIRO.")
public class RiscoAtrasoController {

    private static final String REGRAS = "Tres regras, de 0 a 2 pontos cada, sobre as cobrancas nao canceladas "
            + "dos ultimos 12 meses (e as vencidas em aberto de qualquer data). Atrasos anteriores (pagas depois "
            + "do vencimento mais vencidas em aberto): 2 ou 3 = 1 ponto, 4 ou mais = 2. Dias medios de atraso: "
            + "6 a 15 = 1 ponto, mais de 15 = 2. Valor vencido em aberto: algum = 1 ponto, 2 vezes o valor medio "
            + "das cobrancas do aluno ou mais = 2. Nivel: 0-1 BAIXO, 2-3 MEDIO, 4 ou mais ALTO.";

    private final RiscoAtrasoService riscoAtrasoService;

    public RiscoAtrasoController(RiscoAtrasoService riscoAtrasoService) {
        this.riscoAtrasoService = riscoAtrasoService;
    }

    @GetMapping("/alunos/{alunoId}/financeiro/risco")
    @Operation(summary = "Risco de atraso de um aluno", description = REGRAS + " Perfis: MASTER, FINANCEIRO.")
    @ApiResponse(responseCode = "200", description = "Nivel, pontos, motivo, indicadores e o que cada regra mediu.")
    @ApiResponse(responseCode = "404", description = "Aluno nao encontrado na escola do token.")
    public RiscoAtrasoResponse doAluno(@PathVariable UUID alunoId) {
        return riscoAtrasoService.doAluno(alunoId);
    }

    @GetMapping("/cobrancas/risco-atraso")
    @Operation(summary = "Risco de atraso dos alunos da escola",
            description = "Um item por aluno com cobranca considerada, do maior risco para o menor. " + REGRAS
                    + " Perfis: MASTER, FINANCEIRO.")
    @ApiResponse(responseCode = "200", description = "Alunos do maior para o menor risco.")
    @ApiResponse(responseCode = "400", description = "Nivel invalido.")
    public List<RiscoAtrasoResponse> daEscola(
            @Parameter(description = "So os alunos desse nivel para cima (BAIXO, MEDIO ou ALTO). Vazio traz todos.")
            @RequestParam(required = false) NivelRiscoAtraso nivelMinimo) {
        return riscoAtrasoService.daEscola(nivelMinimo);
    }
}
