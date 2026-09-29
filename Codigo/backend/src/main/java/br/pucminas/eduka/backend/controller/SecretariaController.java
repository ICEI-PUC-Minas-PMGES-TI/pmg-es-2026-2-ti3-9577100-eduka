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

import br.pucminas.eduka.backend.dto.SecretariaRequest;
import br.pucminas.eduka.backend.dto.SecretariaResponse;
import br.pucminas.eduka.backend.dto.SecretariaUpdateRequest;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.SecretariaService;
import jakarta.validation.Valid;

/**
 * Secretarias da escola do usuario autenticado. Nao ha rota com
 * {@code escolaId} na URL: o tenant e sempre o do token (ver
 * {@link SecretariaService}).
 *
 * <p>
 * O gate de modulo em {@code RotasPorPerfil} libera {@code /usuarios/**} para
 * SUPER_ADMIN, MASTER e SECRETARIA, mas este recurso especifico e gestao de
 * pessoal: quem decide quem trabalha como SECRETARIA na escola e o MASTER, e
 * so ele. Por isso {@link Perfis#GESTAO} aqui em cima, restringindo de novo
 * dentro do que o gate de modulo ja deixou passar.
 * </p>
 */
@RestController
@RequestMapping("/usuarios/secretarias")
@PreAuthorize(Perfis.GESTAO)
public class SecretariaController {

    private final SecretariaService secretariaService;

    public SecretariaController(SecretariaService secretariaService) {
        this.secretariaService = secretariaService;
    }

    @GetMapping
    public List<SecretariaResponse> listar(@RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo) {
        return secretariaService.listar(nome, ativo);
    }

    @GetMapping("/{id}")
    public SecretariaResponse buscarPorId(@PathVariable UUID id) {
        return secretariaService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<SecretariaResponse> cadastrar(@Valid @RequestBody SecretariaRequest request) {
        SecretariaResponse response = secretariaService.cadastrar(request);
        return ResponseEntity.created(URI.create("/usuarios/secretarias/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public SecretariaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody SecretariaUpdateRequest request) {
        return secretariaService.atualizar(id, request);
    }

    /** Exclusao logica: a secretaria fica inativa, o registro continua. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        secretariaService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ativar")
    public SecretariaResponse ativar(@PathVariable UUID id) {
        return secretariaService.ativar(id);
    }
}
