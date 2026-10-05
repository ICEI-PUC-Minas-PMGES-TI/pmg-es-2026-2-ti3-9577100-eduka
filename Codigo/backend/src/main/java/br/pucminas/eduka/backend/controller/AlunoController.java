// Codigo/backend/src/main/java/br/pucminas/eduka/backend/controller/AlunoController.java
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.AlunoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Alunos", description = "Alunos da escola do usuario autenticado.")
public class AlunoController {

    private final AlunoService alunoService;

    public AlunoController(AlunoService alunoService) {
        this.alunoService = alunoService;
    }

    /** {@code nome}: filtro parcial (enturmacao), decifrado em memoria pelo servico. */
    @GetMapping
    @PreAuthorize(Perfis.LEITURA_ACADEMICA)
    @Operation(summary = "Lista os alunos", description = "Perfis: MASTER, SECRETARIA, FINANCEIRO, PROFESSOR.")
    public List<AlunoResponse> listar(
            @Parameter(description = "Filtro parcial pelo nome") @RequestParam(required = false) String nome) {
        return alunoService.listar(nome);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.LEITURA_ACADEMICA)
    @Operation(summary = "Busca um aluno pelo id", description = "Perfis: MASTER, SECRETARIA, FINANCEIRO, PROFESSOR.")
    public AlunoResponse buscarPorId(@PathVariable UUID id) {
        return alunoService.buscarPorId(id);
    }

    @PostMapping
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Cadastra um aluno", description = "Perfis: MASTER, SECRETARIA.")
    public ResponseEntity<AlunoResponse> cadastrar(@RequestBody AlunoRequest request) {
        AlunoResponse response = alunoService.cadastrar(request);
        return ResponseEntity.created(URI.create("/alunos/" + response.id())).body(response);
    }
}
