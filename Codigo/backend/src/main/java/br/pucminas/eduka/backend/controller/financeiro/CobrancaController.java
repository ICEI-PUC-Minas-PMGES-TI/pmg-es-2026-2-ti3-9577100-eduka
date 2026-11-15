package br.pucminas.eduka.backend.controller.financeiro;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.financeiro.AtualizarCobrancaRequest;
import br.pucminas.eduka.backend.dto.financeiro.BaixaManualRequest;
import br.pucminas.eduka.backend.dto.financeiro.CobrancaRequest;
import br.pucminas.eduka.backend.dto.financeiro.CobrancaResponse;
import br.pucminas.eduka.backend.dto.financeiro.ComprovanteResponse;
import br.pucminas.eduka.backend.dto.financeiro.PaginaCobrancasResponse;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaFiltro;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.financeiro.CobrancaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final PaymentRoutingService paymentRoutingService;

    public CobrancaController(CobrancaService cobrancaService, PaymentRoutingService paymentRoutingService) {
        this.cobrancaService = cobrancaService;
        this.paymentRoutingService = paymentRoutingService;
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
                    + "(inclusivo), e `nome`, parte do nome do aluno. Ordenado pelo vencimento. `pagina` comeca "
                    + "em 0; `tamanho` vai de 1 a 100. `totaisPorStatus` traz quantidade e valor de cada situacao "
                    + "com os mesmos filtros, menos os de situacao e de nome.")
    @ApiResponse(responseCode = "200", description = "A pagina de cobrancas, com os totais por situacao.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou intervalo de vencimento invalidos.")
    public PaginaCobrancasResponse listarPaginado(@RequestParam(required = false) UUID alunoId,
            @RequestParam(required = false) Long periodoLetivoId,
            @RequestParam(required = false) StatusCobranca status,
            @RequestParam(required = false) LocalDate vencimentoDe,
            @RequestParam(required = false) LocalDate vencimentoAte,
            @Parameter(description = "Parte do nome do aluno") @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return cobrancaService.listarPaginado(
                new CobrancaFiltro(alunoId, periodoLetivoId, status, vencimentoDe, vencimentoAte), nome, pagina,
                tamanho);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Busca uma cobranca")
    @ApiResponse(responseCode = "200", description = "A cobranca.")
    @ApiResponse(responseCode = "404", description = "Cobranca nao encontrada na escola do token.")
    public CobrancaResponse buscar(@PathVariable UUID id) {
        return cobrancaService.buscar(id);
    }

    @GetMapping("/{id}/comprovante")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Comprovante do pagamento online", description = "Montado pelo Eduka, com a escola como "
            + "beneficiaria e o CPF do pagador mascarado. A baixa manual nao tem comprovante.")
    @ApiResponse(responseCode = "200", description = "O comprovante.")
    @ApiResponse(responseCode = "404", description = "`COMPROVANTE_INDISPONIVEL` enquanto nao ha pagamento online "
            + "registrado; sem codigo se a cobranca nao e da escola.")
    public ComprovanteResponse comprovante(@PathVariable UUID id) {
        return paymentRoutingService.comprovanteCobranca(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Lanca uma cobranca para um aluno",
            description = "Cria a cobranca como PENDENTE. `matriculaId` e `responsavelId` sao opcionais.")
    @ApiResponse(responseCode = "400", description = "Corpo invalido, ou a matricula ou o responsavel nao sao do "
            + "aluno.")
    @ApiResponse(responseCode = "404", description = "Aluno, matricula ou responsavel nao sao da escola.")
    public ResponseEntity<CobrancaResponse> criar(@Valid @RequestBody CobrancaRequest request) {
        CobrancaResponse response = cobrancaService.criar(request);
        return ResponseEntity.created(URI.create("/cobrancas/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Edita uma cobranca",
            description = "Substitui matricula, responsavel, descricao, valor e vencimento; o aluno nao muda. "
                    + "Depois que alguem clicou em Pagar, nao edita mais: para mudar, cancele e lance outra.")
    @ApiResponse(responseCode = "200", description = "A cobranca editada.")
    @ApiResponse(responseCode = "400", description = "Corpo invalido, cobranca ja paga ou cancelada, ou matricula "
            + "ou responsavel que nao sao do aluno.")
    @ApiResponse(responseCode = "404", description = "Cobranca, matricula ou responsavel nao sao da escola.")
    @ApiResponse(responseCode = "409", description = "`COBRANCA_NAO_EDITAVEL` depois do Pagar; "
            + "`ALTERADO_AO_MESMO_TEMPO` se o Pagar gravou ao mesmo tempo.")
    public CobrancaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarCobrancaRequest request) {
        return cobrancaService.atualizar(id, request);
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Cancela uma cobranca",
            description = "A cobranca continua no historico, como CANCELADA. Se ja foi emitida, e removida antes "
                    + "no Asaas; se o Asaas ja recebeu, o pagamento achado fica registrado.")
    @ApiResponse(responseCode = "200", description = "A cobranca cancelada.")
    @ApiResponse(responseCode = "400", description = "Cobranca ja cancelada.")
    @ApiResponse(responseCode = "404", description = "Cobranca nao encontrada na escola do token.")
    @ApiResponse(responseCode = "409", description = "`COBRANCA_JA_PAGA` se ja foi paga, aqui ou no Asaas; "
            + "`PAGAMENTO_EM_PROCESSAMENTO` enquanto o pagamento esta sendo gerado.")
    @ApiResponse(responseCode = "503", description = "`PROVEDOR_INDISPONIVEL` ou `PROVEDOR_NAO_CONFIGURADO` ao "
            + "remover no Asaas a cobranca ja emitida.")
    public CobrancaResponse cancelar(@PathVariable UUID id) {
        return cobrancaService.cancelar(id);
    }

    @PatchMapping("/{id}/pagar")
    @PreAuthorize(Perfis.FINANCEIRO)
    @Operation(summary = "Da baixa manual no pagamento de uma cobranca",
            description = "Registra o pagamento recebido fora do Eduka: `valorPago` (pode diferir do valor da "
                    + "cobranca), `dataPagamento` (ate hoje, no fuso da escola) e `forma` (DINHEIRO, PIX, "
                    + "TRANSFERENCIA, CARTAO ou BOLETO). Marca a cobranca como PAGA, registra no LOG_AUDITORIA e "
                    + "envia o e-mail de pagamento confirmado ao responsavel financeiro (ou ao aluno). Se o aluno "
                    + "ja gerou PIX ou boleto, a cobranca e removida antes no Asaas.")
    @ApiResponse(responseCode = "200", description = "A cobranca paga.")
    @ApiResponse(responseCode = "400", description = "Corpo invalido, dia do pagamento futuro, ou cobranca ja paga "
            + "ou cancelada.")
    @ApiResponse(responseCode = "404", description = "Cobranca nao encontrada na escola do token.")
    @ApiResponse(responseCode = "409", description = "`COBRANCA_JA_PAGA` se o Asaas ja recebeu (fica paga sem "
            + "baixa manual); `PAGAMENTO_EM_PROCESSAMENTO` enquanto o pagamento esta sendo gerado.")
    @ApiResponse(responseCode = "503", description = "`PROVEDOR_INDISPONIVEL` ou `PROVEDOR_NAO_CONFIGURADO` ao "
            + "remover no Asaas a cobranca ja emitida.")
    public CobrancaResponse pagar(@PathVariable UUID id, @Valid @RequestBody BaixaManualRequest request) {
        return cobrancaService.confirmarPagamento(id, request);
    }
}
