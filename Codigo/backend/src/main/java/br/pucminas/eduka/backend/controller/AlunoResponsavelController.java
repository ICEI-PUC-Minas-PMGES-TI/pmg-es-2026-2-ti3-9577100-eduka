package br.pucminas.eduka.backend.controller;

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
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.ResponsavelRequest;
import br.pucminas.eduka.backend.dto.ResponsavelResponse;
import br.pucminas.eduka.backend.dto.VinculoResponsavelRequest;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.ResponsavelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Vinculo entre aluno e responsavel. {@code responsavelId} e o id do
 * cadastro em {@code /responsaveis}; aluno ou responsavel de outra escola
 * respondem 404.
 */
@RestController
@RequestMapping("/alunos/{alunoId}/responsaveis")
@PreAuthorize(Perfis.RESPONSAVEIS)
@Tag(name = "Responsaveis", description = "Responsaveis (legal/financeiro) dos alunos. Perfis: MASTER, SECRETARIA, FINANCEIRO.")
public class AlunoResponsavelController {

    private final ResponsavelService responsavelService;

    public AlunoResponsavelController(ResponsavelService responsavelService) {
        this.responsavelService = responsavelService;
    }

    @GetMapping
    @Operation(summary = "Lista os responsaveis de um aluno")
    public List<ResponsavelResponse> listar(@PathVariable UUID alunoId) {
        return responsavelService.listarDoAluno(alunoId);
    }

    @PostMapping
    @Operation(summary = "Cadastra um responsavel e o vincula ao aluno", description = "CPF ja cadastrado na escola "
            + "reaproveita o responsavel (irmaos). `financeiro: true` exige CPF valido, porque ele e o pagador no "
            + "Asaas, e tira a marca do anterior. Responde 409 se ele ja esta vinculado ao aluno.")
    public ResponseEntity<ResponsavelResponse> incluir(@PathVariable UUID alunoId,
            @Valid @RequestBody ResponsavelRequest request) {
        ResponsavelResponse response = responsavelService.incluirNoAluno(alunoId, request);
        return ResponseEntity.created(URI.create("/alunos/" + alunoId + "/responsaveis/" + response.responsavelId()))
                .body(response);
    }

    /** Idempotente: cria o vinculo ou atualiza parentesco/financeiro do existente. */
    @PutMapping("/{responsavelId}")
    @Operation(summary = "Vincula um responsavel ao aluno", description = "Cria ou atualiza o vinculo. `financeiro: true` exige CPF valido, porque ele e o pagador no Asaas, e tira a marca do responsavel financeiro anterior do aluno.")
    public ResponsavelResponse vincular(@PathVariable UUID alunoId, @PathVariable UUID responsavelId,
            @Valid @RequestBody VinculoResponsavelRequest request) {
        return responsavelService.vincular(alunoId, responsavelId, request);
    }

    @DeleteMapping("/{responsavelId}")
    @Operation(summary = "Desvincula um responsavel do aluno", description = "O cadastro do responsavel continua. Responde 409 se ha cobranca em aberto do aluno em nome dele.")
    public ResponseEntity<Void> desvincular(@PathVariable UUID alunoId, @PathVariable UUID responsavelId) {
        responsavelService.desvincular(alunoId, responsavelId);
        return ResponseEntity.noContent().build();
    }
}
