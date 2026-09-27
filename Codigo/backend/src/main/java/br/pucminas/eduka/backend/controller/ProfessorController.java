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

import br.pucminas.eduka.backend.dto.ProfessorRequest;
import br.pucminas.eduka.backend.dto.ProfessorResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.ProfessorService;
import jakarta.validation.Valid;

/**
 * Professores da escola do usuario autenticado. Nao ha rota com
 * {@code escolaId} na URL: o tenant e sempre o do token (ver
 * {@link ProfessorService}).
 *
 * <p>
 * Leitura e escrita so para gestao e secretaria: o cadastro traz CPF e
 * contato, que professor e financeiro nao precisam ver.
 * </p>
 */
@RestController
@RequestMapping("/professores")
@PreAuthorize(Perfis.CADASTRO_ACADEMICO)
public class ProfessorController {

    private final ProfessorService professorService;

    public ProfessorController(ProfessorService professorService) {
        this.professorService = professorService;
    }

    @GetMapping
    public List<ProfessorResponse> listar(@RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo) {
        return professorService.listar(nome, ativo);
    }

    @GetMapping("/{id}")
    public ProfessorResponse buscarPorId(@PathVariable UUID id) {
        return professorService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ProfessorResponse> cadastrar(@Valid @RequestBody ProfessorRequest request) {
        ProfessorResponse response = professorService.cadastrar(request);
        return ResponseEntity.created(URI.create("/professores/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public ProfessorResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProfessorRequest request) {
        return professorService.atualizar(id, request);
    }

    /** Exclusao logica: o professor fica inativo, o registro continua. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        professorService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ativar")
    public ProfessorResponse ativar(@PathVariable UUID id) {
        return professorService.ativar(id);
    }
}
