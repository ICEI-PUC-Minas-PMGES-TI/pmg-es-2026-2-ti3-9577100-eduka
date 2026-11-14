package br.pucminas.eduka.backend.controller.financeiro;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.financeiro.CategoriaDespesaRequest;
import br.pucminas.eduka.backend.dto.financeiro.CategoriaDespesaResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.financeiro.CategoriaDespesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Categorias de despesa da escola do usuario autenticado. */
@RestController
@RequestMapping("/categorias-despesa")
@Tag(name = "Despesas", description = "Despesas da escola. Todas as operacoes: MASTER, FINANCEIRO.")
public class CategoriaDespesaController {

    private final CategoriaDespesaService categoriaService;

    public CategoriaDespesaController(CategoriaDespesaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Lista as categorias de despesa",
            description = "Ordenado pelo nome. Na primeira consulta, a escola sem nenhuma categoria recebe as "
                    + "padrao: Salários, Contas, Materiais, Consertos, Limpeza e Outros.")
    public List<CategoriaDespesaResponse> listar() {
        return categoriaService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Cadastra uma categoria de despesa",
            description = "O nome nao se repete na escola, sem diferenciar maiusculas.")
    @ApiResponse(responseCode = "409", description = "A escola ja tem categoria com esse nome.")
    public ResponseEntity<CategoriaDespesaResponse> criar(@Valid @RequestBody CategoriaDespesaRequest request) {
        CategoriaDespesaResponse response = categoriaService.criar(request);
        return ResponseEntity.created(URI.create("/categorias-despesa/" + response.id())).body(response);
    }
}
