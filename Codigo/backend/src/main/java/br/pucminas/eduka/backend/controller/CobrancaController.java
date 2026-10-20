package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.AtualizarCobrancaRequest;
import br.pucminas.eduka.backend.dto.CobrancaRequest;
import br.pucminas.eduka.backend.dto.CobrancaResponse;
import br.pucminas.eduka.backend.dto.PaginaCobrancasResponse;
import br.pucminas.eduka.backend.repository.CobrancaFiltro;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.CobrancaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Cobrancas dos alunos da escola do usuario autenticado. So gestao e
 * financeiro entram no modulo.
 */
@RestController
@RequestMapping("/cobrancas")
@Tag(name = "Cobrancas", description = "Cobrancas dos alunos. Todas as operacoes: MASTER, FINANCEIRO.")
public class CobrancaController {

    private final CobrancaService cobrancaService;

    public CobrancaController(CobrancaService cobrancaService) {
        this.cobrancaService = cobrancaService;
    }

    @GetMapping
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Lista as cobrancas",
            description = "Filtros opcionais por aluno, situacao (PENDENTE, EM_ATRASO, PAGA, CANCELADA) e "
                    + "intervalo de vencimento. Ordenado pelo vencimento.")
    public List<CobrancaResponse> listar(@RequestParam(required = false) UUID alunoId,
            @RequestParam(required = false) StatusCobranca status,
            @RequestParam(required = false) LocalDate vencimentoDe,
            @RequestParam(required = false) LocalDate vencimentoAte) {
        return cobrancaService.listar(alunoId, status, vencimentoDe, vencimentoAte);
    }

    @GetMapping("/pagina")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Lista as cobrancas em paginas, com os totais por situacao",
            description = "Filtros opcionais por aluno, periodo letivo (pela turma da matricula; cobranca avulsa "
                    + "fica de fora), situacao (PENDENTE, EM_ATRASO, PAGA, CANCELADA) e intervalo de vencimento "
                    + "(inclusivo). Ordenado pelo vencimento. `pagina` comeca em 0; `tamanho` vai de 1 a 100. "
                    + "`totaisPorStatus` traz quantidade e valor de cada situacao com os mesmos filtros, menos o "
                    + "de situacao. 400 se a pagina, o tamanho ou o intervalo forem invalidos.")
    public PaginaCobrancasResponse listarPaginado(@RequestParam(required = false) UUID alunoId,
            @RequestParam(required = false) Long periodoLetivoId,
            @RequestParam(required = false) StatusCobranca status,
            @RequestParam(required = false) LocalDate vencimentoDe,
            @RequestParam(required = false) LocalDate vencimentoAte,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return cobrancaService.listarPaginado(
                new CobrancaFiltro(alunoId, periodoLetivoId, status, vencimentoDe, vencimentoAte), pagina, tamanho);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Busca uma cobranca")
    public CobrancaResponse buscar(@PathVariable UUID id) {
        return cobrancaService.buscar(id);
    }

    @PostMapping
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Lanca uma cobranca para um aluno",
            description = "Cria a cobranca como PENDENTE. `matriculaId` e `responsavelId` sao opcionais. 404 se "
                    + "aluno, matricula ou responsavel nao forem da escola; 400 se a matricula ou o responsavel "
                    + "nao forem do aluno.")
    public ResponseEntity<CobrancaResponse> criar(@Valid @RequestBody CobrancaRequest request) {
        CobrancaResponse response = cobrancaService.criar(request);
        return ResponseEntity.created(URI.create("/cobrancas/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Edita uma cobranca",
            description = "Substitui matricula, responsavel, descricao, valor e vencimento; o aluno nao muda. "
                    + "400 se a cobranca ja foi paga ou cancelada.")
    public CobrancaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarCobrancaRequest request) {
        return cobrancaService.atualizar(id, request);
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Cancela uma cobranca",
            description = "A cobranca continua no historico, como CANCELADA. 400 se ja foi paga ou cancelada.")
    public CobrancaResponse cancelar(@PathVariable UUID id) {
        return cobrancaService.cancelar(id);
    }

    @PatchMapping("/{id}/pagar")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Da baixa manual no pagamento de uma cobranca",
            description = "Marca a cobranca como PAGA, registra no LOG_AUDITORIA e envia o e-mail de pagamento "
                    + "confirmado ao responsavel financeiro (ou ao aluno). 400 se ja foi paga ou cancelada.")
    public CobrancaResponse pagar(@PathVariable UUID id) {
        return cobrancaService.confirmarPagamento(id);
    }
}
