package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort.Direction;
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

import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.dto.ProfessorRequest;
import br.pucminas.eduka.backend.dto.ProfessorResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.ProfessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Professores", description = "Professores da escola do usuario autenticado. Perfis: MASTER, SECRETARIA.")
public class ProfessorController {

    private final ProfessorService professorService;

    public ProfessorController(ProfessorService professorService) {
        this.professorService = professorService;
    }

    @GetMapping
    @Operation(summary = "Lista os professores")
    public List<ProfessorResponse> listar(
            @Parameter(description = "Filtro parcial pelo nome") @RequestParam(required = false) String nome,
            @Parameter(description = "Filtra por ativos (true) ou inativos (false)") @RequestParam(required = false) Boolean ativo) {
        return professorService.listar(nome, ativo);
    }

    @GetMapping("/pagina")
    @Operation(summary = "Lista os professores em paginas", description = "Filtros opcionais por parte do nome e "
            + "por `ativo`. `pagina` comeca em 0; `tamanho` vai de 1 a 100. `ordenarPor`: NOME (padrao) ou "
            + "DATA_ADMISSAO; `direcao`: ASC (padrao) ou DESC.")
    @ApiResponse(responseCode = "200", description = "A pagina de professores.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou ordenacao invalidos.")
    public PaginaResponse<ProfessorResponse> listarPaginado(
            @Parameter(description = "Filtro parcial pelo nome") @RequestParam(required = false) String nome,
            @Parameter(description = "Filtra por ativos (true) ou inativos (false)") @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = Paginacao.TAMANHO_PADRAO) int tamanho,
            @RequestParam(defaultValue = "NOME") Ordenacao.Professores ordenarPor,
            @RequestParam(required = false) Direction direcao) {
        return professorService.listarPaginado(nome, ativo, new Paginacao(pagina, tamanho), ordenarPor, direcao);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um professor pelo id")
    public ProfessorResponse buscarPorId(@PathVariable UUID id) {
        return professorService.buscarPorId(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra um professor", description = "Cria tambem o login e envia o e-mail de primeiro acesso.")
    public ResponseEntity<ProfessorResponse> cadastrar(@Valid @RequestBody ProfessorRequest request) {
        ProfessorResponse response = professorService.cadastrar(request);
        return ResponseEntity.created(URI.create("/professores/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de um professor")
    public ProfessorResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProfessorRequest request) {
        return professorService.atualizar(id, request);
    }

    /** Afastamento (licenca, ferias...): o professor e o login ficam inativos, o registro continua. */
    @PatchMapping("/{id}/inativar")
    @Operation(summary = "Inativa um professor", description = "Afastamento: professor e login ficam inativos, o registro continua.")
    public ProfessorResponse inativar(@PathVariable UUID id) {
        return professorService.inativar(id);
    }

    /**
     * Saida da escola: apaga o professor e o login. Recusado (409) se ele ja
     * tem historico; nesse caso, inativar.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um professor", description = "Apaga professor e login. Responde 409 se ele ja tem historico; nesse caso, inativar.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        professorService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ativar")
    @Operation(summary = "Reativa um professor")
    public ProfessorResponse ativar(@PathVariable UUID id) {
        return professorService.ativar(id);
    }

    /** Reenvia o e-mail de primeiro acesso com uma senha provisoria renovada. */
    @PostMapping("/{id}/primeiro-acesso")
    @Operation(summary = "Reenvia o e-mail de primeiro acesso", description = "Gera uma nova senha provisoria.")
    public ResponseEntity<Void> reenviarAcesso(@PathVariable UUID id) {
        professorService.reenviarAcesso(id);
        return ResponseEntity.noContent().build();
    }
}
