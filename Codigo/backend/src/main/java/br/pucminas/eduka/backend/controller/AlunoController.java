package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.service.AlunoService;

/**
 * Alunos da escola do usuario autenticado. Nao ha rota com {@code escolaId}
 * na URL: o tenant e sempre o do token (ver {@link AlunoService}).
 *
 * <p>
 * Leitura: perfis administrativos e professor. Escrita: gestao e secretaria.
 * O perfil ALUNO nao acessa esta listagem.
 * </p>
 */
@RestController
@RequestMapping("/alunos")
public class AlunoController {

    private final AlunoService alunoService;

    public AlunoController(AlunoService alunoService) {
        this.alunoService = alunoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MASTER', 'SECRETARIA', 'FINANCEIRO', 'PROFESSOR')")
    public List<AlunoResponse> listar() {
        return alunoService.listar().stream().map(AlunoResponse::de).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MASTER', 'SECRETARIA', 'FINANCEIRO', 'PROFESSOR')")
    public AlunoResponse buscarPorId(@PathVariable UUID id) {
        return AlunoResponse.de(alunoService.buscarPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MASTER', 'SECRETARIA')")
    public ResponseEntity<AlunoResponse> cadastrar(@RequestBody AlunoRequest request) {
        Aluno aluno = alunoService.cadastrar(request);
        return ResponseEntity.created(URI.create("/alunos/" + aluno.getId()))
                .body(AlunoResponse.de(aluno));
    }
}
