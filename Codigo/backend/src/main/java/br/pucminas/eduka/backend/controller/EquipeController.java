package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.dto.MembroEquipeRequest;
import br.pucminas.eduka.backend.dto.MembroEquipeResponse;
import br.pucminas.eduka.backend.dto.MembroEquipeUpdateRequest;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.EquipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Equipe da escola do usuario autenticado: secretaria e financeiro. Nao ha
 * rota com {@code escolaId} na URL: a escola e sempre a do token.
 *
 * <p>
 * O gate de modulo em {@code RotasPorPerfil} libera {@code /usuarios/**} para
 * mais perfis, mas quem decide quem trabalha na escola e o MASTER, e so ele:
 * por isso {@link Perfis#GESTAO} aqui em cima.
 * </p>
 */
@RestController
@RequestMapping("/usuarios/equipe")
@PreAuthorize(Perfis.GESTAO)
@Tag(name = "Equipe", description = "Secretaria e financeiro da escola. Perfil: MASTER.")
public class EquipeController {

    private final EquipeService equipeService;

    public EquipeController(EquipeService equipeService) {
        this.equipeService = equipeService;
    }

    @GetMapping
    @Operation(summary = "Lista a equipe", description = "Em ordem de nome, sem o CPF.")
    public List<MembroEquipeResponse> listar(
            @Parameter(description = "SECRETARIA ou FINANCEIRO; sem ele, os dois") @RequestParam(required = false) PerfilUsuario perfil,
            @Parameter(description = "Filtro parcial pelo nome") @RequestParam(required = false) String nome,
            @Parameter(description = "Filtra por ativos (true) ou inativos (false)") @RequestParam(required = false) Boolean ativo) {
        return equipeService.listar(perfil, nome, ativo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca alguem da equipe pelo id")
    public MembroEquipeResponse buscarPorId(@PathVariable UUID id) {
        return equipeService.buscarPorId(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra alguem na equipe", description = "Cria o login (o e-mail) e envia o primeiro "
            + "acesso, com o CPF de senha provisoria. 409 com CPF ja usado na equipe ou e-mail ja usado como login.")
    public ResponseEntity<MembroEquipeResponse> cadastrar(@Valid @RequestBody MembroEquipeRequest request) {
        MembroEquipeResponse response = equipeService.cadastrar(request);
        return ResponseEntity.created(URI.create("/usuarios/equipe/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de alguem da equipe", description = "O perfil nao muda; trocar o "
            + "e-mail troca o login.")
    public MembroEquipeResponse atualizar(@PathVariable UUID id,
            @Valid @RequestBody MembroEquipeUpdateRequest request) {
        return equipeService.atualizar(id, request);
    }

    @PostMapping("/{id}/primeiro-acesso")
    @Operation(summary = "Reenvia o e-mail de primeiro acesso", description = "Gera uma nova senha provisoria. "
            + "409 se a pessoa ja definiu a senha; 400 se esta inativa.")
    public ResponseEntity<Void> reenviarAcesso(@PathVariable UUID id) {
        equipeService.reenviarAcesso(id);
        return ResponseEntity.noContent().build();
    }

    /** Exclusao logica: o login para de funcionar e o registro continua. */
    @PatchMapping("/{id}/inativar")
    @Operation(summary = "Inativa alguem da equipe", description = "O login para de funcionar; o registro continua.")
    public MembroEquipeResponse inativar(@PathVariable UUID id) {
        return equipeService.inativar(id);
    }

    @PatchMapping("/{id}/ativar")
    @Operation(summary = "Reativa alguem da equipe")
    public MembroEquipeResponse ativar(@PathVariable UUID id) {
        return equipeService.ativar(id);
    }
}
