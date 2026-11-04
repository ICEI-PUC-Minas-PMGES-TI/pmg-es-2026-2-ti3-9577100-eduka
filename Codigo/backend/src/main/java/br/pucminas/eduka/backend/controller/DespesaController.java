package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.dto.AtualizarDespesaRequest;
import br.pucminas.eduka.backend.dto.DespesaRequest;
import br.pucminas.eduka.backend.dto.DespesaResponse;
import br.pucminas.eduka.backend.dto.MudarSituacaoDespesaRequest;
import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.DespesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Despesas da escola do usuario autenticado. So gestao e financeiro entram no
 * modulo.
 */
@RestController
@RequestMapping("/despesas")
@Tag(name = "Despesas", description = "Despesas da escola. Todas as operacoes: MASTER, FINANCEIRO.")
public class DespesaController {

    private final DespesaService despesaService;

    public DespesaController(DespesaService despesaService) {
        this.despesaService = despesaService;
    }

    @GetMapping
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Lista as despesas",
            description = "Filtros opcionais por situacao (A_PAGAR, PAGA, CANCELADA), categoria e intervalo de "
                    + "vencimento (inclusivo). Ordenado pelo vencimento.")
    @ApiResponse(responseCode = "200", description = "As despesas.")
    @ApiResponse(responseCode = "400", description = "Intervalo de vencimento invertido.")
    public List<DespesaResponse> listar(@RequestParam(required = false) SituacaoDespesa situacao,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) LocalDate vencimentoDe,
            @RequestParam(required = false) LocalDate vencimentoAte) {
        return despesaService.listar(situacao, categoriaId, vencimentoDe, vencimentoAte);
    }

    @GetMapping("/pagina")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Lista as despesas em paginas",
            description = "Mesmos filtros da listagem. `pagina` comeca em 0; `tamanho` vai de 1 a 100. "
                    + "`ordenarPor`: VENCIMENTO (padrao), VALOR ou DESCRICAO; `direcao`: ASC (padrao) ou DESC.")
    @ApiResponse(responseCode = "200", description = "A pagina de despesas.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho, ordenacao ou intervalo de vencimento "
            + "invalidos.")
    public PaginaResponse<DespesaResponse> listarPaginado(@RequestParam(required = false) SituacaoDespesa situacao,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) LocalDate vencimentoDe,
            @RequestParam(required = false) LocalDate vencimentoAte,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = Paginacao.TAMANHO_PADRAO) int tamanho,
            @RequestParam(defaultValue = "VENCIMENTO") Ordenacao.Despesas ordenarPor,
            @RequestParam(required = false) Direction direcao) {
        return despesaService.listarPaginado(situacao, categoriaId, vencimentoDe, vencimentoAte,
                new Paginacao(pagina, tamanho), ordenarPor, direcao);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Busca uma despesa")
    @ApiResponse(responseCode = "200", description = "A despesa.")
    @ApiResponse(responseCode = "404", description = "Despesa nao encontrada na escola do token.")
    public DespesaResponse buscar(@PathVariable UUID id) {
        return despesaService.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Lanca uma despesa",
            description = "Cria a despesa como A_PAGAR, ou como PAGA com `paga: true`.")
    @ApiResponse(responseCode = "404", description = "Categoria nao encontrada na escola do token.")
    public ResponseEntity<DespesaResponse> criar(@Valid @RequestBody DespesaRequest request) {
        DespesaResponse response = despesaService.criar(request);
        return ResponseEntity.created(URI.create("/despesas/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Edita uma despesa",
            description = "Substitui descricao, categoria, valor e vencimento; a situacao nao muda. Despesa paga "
                    + "tambem e editada.")
    @ApiResponse(responseCode = "200", description = "A despesa editada.")
    @ApiResponse(responseCode = "400", description = "Corpo invalido ou despesa cancelada.")
    @ApiResponse(responseCode = "404", description = "Despesa ou categoria nao encontrada na escola do token.")
    public DespesaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarDespesaRequest request) {
        return despesaService.atualizar(id, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Muda a situacao de uma despesa",
            description = "A_PAGAR passa para PAGA ou CANCELADA; PAGA volta para A_PAGAR; CANCELADA e final e "
                    + "continua no historico. Cada mudanca fica no log de auditoria (PAGAR, ESTORNAR, CANCELAR).")
    @ApiResponse(responseCode = "200", description = "A despesa na nova situacao.")
    @ApiResponse(responseCode = "400", description = "Corpo invalido, ou a despesa ja esta na situacao pedida ou nao "
            + "pode passar para ela.")
    @ApiResponse(responseCode = "404", description = "Despesa nao encontrada na escola do token.")
    public DespesaResponse mudarSituacao(@PathVariable UUID id,
            @Valid @RequestBody MudarSituacaoDespesaRequest request) {
        return despesaService.mudarSituacao(id, request);
    }
}
