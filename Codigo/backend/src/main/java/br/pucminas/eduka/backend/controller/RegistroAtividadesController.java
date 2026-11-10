package br.pucminas.eduka.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.dto.AtividadeResponse;
import br.pucminas.eduka.backend.dto.AutorAtividadeResponse;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.RegistroAtividadesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Registro de atividades (IU28): a trilha de auditoria da escola do token. */
@RestController
@RequestMapping("/auditoria")
@Tag(name = "Registro de atividades", description = "Quem fez o que na escola. Perfil: MASTER.")
public class RegistroAtividadesController {

    private final RegistroAtividadesService registroAtividadesService;

    public RegistroAtividadesController(RegistroAtividadesService registroAtividadesService) {
        this.registroAtividadesService = registroAtividadesService;
    }

    @GetMapping("/pagina")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Lista as atividades em paginas",
            description = "Filtros opcionais por quem fez (`usuarioId`), periodo (`de` e `ate`, inclusivos), "
                    + "entidade e operacao. Ordenado pela data; `direcao`: DESC (padrao, a mais recente primeiro) "
                    + "ou ASC. `pagina` comeca em 0; `tamanho` vai de 1 a 100.")
    @ApiResponse(responseCode = "200", description = "A pagina de atividades.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho, filtro ou periodo invalidos.")
    public PaginaResponse<AtividadeResponse> listar(
            @Parameter(description = "Quem fez") @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            @RequestParam(required = false) EntidadeAuditada entidade,
            @RequestParam(required = false) OperacaoAuditoria operacao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = Paginacao.TAMANHO_PADRAO) int tamanho,
            @RequestParam(required = false) Direction direcao) {
        return registroAtividadesService.listar(usuarioId, de, ate, entidade, operacao,
                new Paginacao(pagina, tamanho), direcao);
    }

    @GetMapping("/autores")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Quem ja fez alguma atividade",
            description = "As opcoes do filtro por pessoa, por nome. Nome nulo: a pessoa nao e mais da escola.")
    public List<AutorAtividadeResponse> listarAutores() {
        return registroAtividadesService.listarAutores();
    }
}
