package br.pucminas.eduka.backend.controller.pessoas;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.pessoas.SolicitacaoCorrecaoRequest;
import br.pucminas.eduka.backend.dto.pessoas.SolicitacaoCorrecaoResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.pessoas.SolicitacaoCorrecaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Pedido de correcao de dados (IU15). O aluno pede pelo portal; a secretaria
 * (ou o MASTER) ve os abertos e marca como atendidos.
 */
@RestController
@Tag(name = "Correcao de dados", description = "Pedidos do aluno para a secretaria corrigir o cadastro.")
public class SolicitacaoCorrecaoController {

    private final SolicitacaoCorrecaoService solicitacaoService;

    public SolicitacaoCorrecaoController(SolicitacaoCorrecaoService solicitacaoService) {
        this.solicitacaoService = solicitacaoService;
    }

    @PostMapping("/portal/aluno/correcoes")
    @PreAuthorize(Perfis.ALUNO)
    @Operation(summary = "Pedir correcao de dados", description = "Perfil: ALUNO. Uma solicitacao aberta por vez.")
    @ApiResponse(responseCode = "201", description = "O pedido aberto.")
    @ApiResponse(responseCode = "409", description = "`SOLICITACAO_ABERTA`: ja ha um pedido aguardando a secretaria.")
    public ResponseEntity<SolicitacaoCorrecaoResponse> abrir(@Valid @RequestBody SolicitacaoCorrecaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitacaoService.abrir(request.mensagem()));
    }

    @GetMapping("/portal/aluno/correcoes/aberta")
    @PreAuthorize(Perfis.ALUNO)
    @Operation(summary = "Meu pedido de correcao aberto", description = "Perfil: ALUNO. 204 se nao ha pedido aberto.")
    public ResponseEntity<SolicitacaoCorrecaoResponse> minhaAberta() {
        return solicitacaoService.minhaAberta().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/alunos/correcoes")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Pedidos de correcao abertos", description = "Perfil: MASTER ou SECRETARIA. Os mais "
            + "antigos primeiro, com o nome e a matricula do aluno.")
    public List<SolicitacaoCorrecaoResponse> listarAbertas() {
        return solicitacaoService.listarAbertas();
    }

    @PatchMapping("/alunos/correcoes/{id}/atender")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Marcar pedido como atendido", description = "Perfil: MASTER ou SECRETARIA.")
    @ApiResponse(responseCode = "200", description = "O pedido atendido.")
    @ApiResponse(responseCode = "404", description = "Pedido nao encontrado na escola do token.")
    public SolicitacaoCorrecaoResponse atender(@PathVariable UUID id) {
        return solicitacaoService.atender(id);
    }
}
