package br.pucminas.eduka.backend.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.HistoricoFinanceiroResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.CobrancaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Historico financeiro de um aluno, visto pela escola. Mesmo publico de
 * {@code /cobrancas}: a rota-mae {@code /alunos} deixa entrar secretaria e
 * professor, o {@code @PreAuthorize} restringe a gestao e financeiro.
 */
@RestController
@RequestMapping("/alunos/{alunoId}/financeiro")
@PreAuthorize(Perfis.FINANCEIRO)
@Tag(name = "Cobrancas", description = "Cobrancas dos alunos. Todas as operacoes: MASTER, FINANCEIRO.")
public class AlunoFinanceiroController {

    private final CobrancaService cobrancaService;

    public AlunoFinanceiroController(CobrancaService cobrancaService) {
        this.cobrancaService = cobrancaService;
    }

    @GetMapping
    @Operation(summary = "Historico financeiro de um aluno", description = "Resumo e cobrancas, da mais recente para a mais antiga pelo vencimento. 404 se o aluno nao e da escola.")
    public HistoricoFinanceiroResponse historico(@PathVariable UUID alunoId) {
        return cobrancaService.historicoDoAluno(alunoId);
    }
}
