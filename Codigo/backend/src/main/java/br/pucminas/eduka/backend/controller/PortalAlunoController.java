package br.pucminas.eduka.backend.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.dto.AvaliacaoDoAlunoResponse;
import br.pucminas.eduka.backend.dto.BoletimTurmaResponse;
import br.pucminas.eduka.backend.dto.HistoricoFinanceiroResponse;
import br.pucminas.eduka.backend.dto.TurmaDoAlunoResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.AlunoService;
import br.pucminas.eduka.backend.service.CobrancaService;
import br.pucminas.eduka.backend.service.PortalAlunoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Area pessoal do aluno autenticado. Nao ha {@code alunoId} nem
 * {@code escolaId} na URL: os dois saem do token (ver
 * {@link AlunoService#buscarMeusDados()}).
 */
@RestController
@RequestMapping("/portal/aluno")
@PreAuthorize(Perfis.ALUNO)
@Tag(name = "Portal do aluno", description = "Area pessoal do aluno autenticado. Perfil: ALUNO.")
public class PortalAlunoController {

    private final AlunoService alunoService;
    private final PortalAlunoService portalAlunoService;
    private final CobrancaService cobrancaService;

    public PortalAlunoController(AlunoService alunoService, PortalAlunoService portalAlunoService,
            CobrancaService cobrancaService) {
        this.alunoService = alunoService;
        this.portalAlunoService = portalAlunoService;
        this.cobrancaService = cobrancaService;
    }

    /** Dados cadastrais do proprio aluno, com endereco e responsaveis. */
    @GetMapping("/me")
    @Operation(summary = "Dados do aluno autenticado", description = "Responde 404 se o login nao e de um aluno cadastrado na escola.")
    public AlunoResponse meusDados() {
        return alunoService.buscarMeusDados();
    }

    /** Turmas em que o aluno esta (ou esteve) matriculado, com a grade e os professores. */
    @GetMapping("/turmas")
    @Operation(summary = "Turmas do aluno autenticado", description = "Ativas primeiro; matricula cancelada fica de fora. Cada turma traz as disciplinas e quem leciona.")
    public List<TurmaDoAlunoResponse> minhasTurmas() {
        return portalAlunoService.listarMinhasTurmas();
    }

    /** Boletim por turma e disciplina, com as notas lancadas e os totais de pontos. */
    @GetMapping("/boletim")
    @Operation(summary = "Boletim do aluno autenticado", description = "Por turma, cada disciplina da grade com as avaliacoes, a nota em cada uma (nula se ainda nao lancada), os totais de pontos e a frequencia (aulas, faltas, percentual de presenca e alerta quando as faltas passam de 25% das aulas; nula se a disciplina ainda nao teve aula).")
    public List<BoletimTurmaResponse> boletim() {
        return portalAlunoService.boletim();
    }

    /** Provas, trabalhos e tarefas das turmas ativas, por data. */
    @GetMapping("/avaliacoes")
    @Operation(summary = "Agenda de avaliacoes do aluno autenticado", description = "Avaliacoes das turmas em que o aluno esta ativo, por data, com a nota quando ja lancada.")
    public List<AvaliacaoDoAlunoResponse> agenda() {
        return portalAlunoService.agenda();
    }

    /** Cobrancas do aluno (todas, inclusive canceladas) com o resumo do que esta em aberto, em atraso e pago. */
    @GetMapping("/financeiro")
    @Operation(summary = "Historico financeiro do aluno autenticado", description = "Resumo e cobrancas, da mais recente para a mais antiga pelo vencimento. Responde 404 se o login nao e de um aluno cadastrado na escola.")
    public HistoricoFinanceiroResponse financeiro() {
        return cobrancaService.meuHistorico();
    }
}
