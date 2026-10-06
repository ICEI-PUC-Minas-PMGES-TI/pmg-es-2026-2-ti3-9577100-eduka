package br.pucminas.eduka.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.AlunoService;
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

    public PortalAlunoController(AlunoService alunoService) {
        this.alunoService = alunoService;
    }

    /** Dados cadastrais do proprio aluno, com endereco e responsaveis. */
    @GetMapping("/me")
    @Operation(summary = "Dados do aluno autenticado", description = "Responde 404 se o login nao e de um aluno cadastrado na escola.")
    public AlunoResponse meusDados() {
        return alunoService.buscarMeusDados();
    }
}
