package br.pucminas.eduka.backend.controller.pessoas;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.pessoas.CadastroResponsavelRequest;
import br.pucminas.eduka.backend.dto.pessoas.CadastroResponsavelResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.pessoas.ResponsavelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Cadastro de responsaveis da escola do usuario autenticado. Nao ha rota com
 * {@code escolaId} na URL: o tenant e sempre o do token (ver
 * {@link ResponsavelService}). O vinculo com alunos fica em
 * {@link AlunoResponsavelController}.
 */
@RestController
@RequestMapping("/responsaveis")
@PreAuthorize(Perfis.RESPONSAVEIS)
@Tag(name = "Responsaveis", description = "Responsaveis (legal/financeiro) dos alunos. Perfis: MASTER, SECRETARIA, FINANCEIRO.")
public class ResponsavelController {

    private final ResponsavelService responsavelService;

    public ResponsavelController(ResponsavelService responsavelService) {
        this.responsavelService = responsavelService;
    }

    @GetMapping
    @Operation(summary = "Lista os responsaveis", description = "Sem CPF, endereco e alunos; veja o detalhe.")
    public List<CadastroResponsavelResponse> listar(
            @Parameter(description = "Filtro parcial pelo nome") @RequestParam(required = false) String nome) {
        return responsavelService.listar(nome);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um responsavel pelo id", description = "Inclui os alunos vinculados.")
    public CadastroResponsavelResponse buscarPorId(@PathVariable UUID id) {
        return responsavelService.buscarPorId(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra um responsavel", description = "Ainda sem aluno; vincule em /alunos/{alunoId}/responsaveis. Responde 409 se o CPF ja e de outro responsavel da escola.")
    public ResponseEntity<CadastroResponsavelResponse> cadastrar(
            @Valid @RequestBody CadastroResponsavelRequest request) {
        CadastroResponsavelResponse response = responsavelService.cadastrar(request);
        return ResponseEntity.created(URI.create("/responsaveis/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de um responsavel", description = "Substitui o registro inteiro. Responde 409 se o CPF ja e de outro responsavel da escola.")
    public CadastroResponsavelResponse atualizar(@PathVariable UUID id,
            @Valid @RequestBody CadastroResponsavelRequest request) {
        return responsavelService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um responsavel", description = "Responde 409 se ainda ha aluno vinculado ou cobranca lancada para ele.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        responsavelService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
