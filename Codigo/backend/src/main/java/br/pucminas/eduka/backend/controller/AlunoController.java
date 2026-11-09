// Codigo/backend/src/main/java/br/pucminas/eduka/backend/controller/AlunoController.java
package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.AlunoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Alunos da escola do usuario autenticado. Nao ha rota com {@code escolaId}
 * na URL: o tenant e sempre o do token (ver {@link AlunoService}).
 *
 * <p>
 * Leitura: perfis administrativos. Escrita: gestao e secretaria. O professor
 * ve so os alunos das proprias turmas, pelo portal; o perfil ALUNO, os proprios
 * dados, tambem pelo portal.
 * </p>
 */
@RestController
@RequestMapping("/alunos")
@Tag(name = "Alunos", description = "Alunos da escola do usuario autenticado.")
public class AlunoController {

    private final AlunoService alunoService;

    public AlunoController(AlunoService alunoService) {
        this.alunoService = alunoService;
    }

    /** {@code nome}: filtro parcial (enturmacao), decifrado em memoria pelo servico. */
    @GetMapping
    @PreAuthorize(Perfis.LEITURA_ALUNOS)
    @Operation(summary = "Lista os alunos", description = "Perfis: MASTER, SECRETARIA, FINANCEIRO.")
    public List<AlunoResponse> listar(
            @Parameter(description = "Filtro parcial pelo nome") @RequestParam(required = false) String nome) {
        return alunoService.listar(nome);
    }

    @GetMapping("/pagina")
    @PreAuthorize(Perfis.LEITURA_ALUNOS)
    @Operation(summary = "Lista os alunos em paginas", description = "Perfis: MASTER, SECRETARIA, FINANCEIRO. "
            + "Filtros opcionais por parte do nome e por situacao (MATRICULADO, TRANCADO, SEM_TURMA). "
            + "`pagina` comeca em 0; `tamanho` vai de 1 a 100. `ordenarPor`: NOME (padrao) ou MATRICULA; "
            + "`direcao`: ASC (padrao) ou DESC.")
    @ApiResponse(responseCode = "200", description = "A pagina de alunos.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou ordenacao invalidos.")
    public PaginaResponse<AlunoResponse> listarPaginado(
            @Parameter(description = "Filtro parcial pelo nome") @RequestParam(required = false) String nome,
            @RequestParam(required = false) AlunoResponse.Situacao situacao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = Paginacao.TAMANHO_PADRAO) int tamanho,
            @RequestParam(defaultValue = "NOME") Ordenacao.Alunos ordenarPor,
            @RequestParam(required = false) Direction direcao) {
        return alunoService.listarPaginado(nome, situacao, new Paginacao(pagina, tamanho), ordenarPor, direcao);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.LEITURA_ALUNOS)
    @Operation(summary = "Busca um aluno pelo id", description = "Perfis: MASTER, SECRETARIA, FINANCEIRO.")
    public AlunoResponse buscarPorId(@PathVariable UUID id) {
        return alunoService.buscarPorId(id);
    }

    @PostMapping
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Cadastra um aluno", description = "Cria tambem o login do aluno e envia o e-mail de primeiro acesso. Responde 409 se o e-mail ja e login de alguem. Perfis: MASTER, SECRETARIA.")
    public ResponseEntity<AlunoResponse> cadastrar(@Valid @RequestBody AlunoRequest request) {
        AlunoResponse response = alunoService.cadastrar(request);
        return ResponseEntity.created(URI.create("/alunos/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Atualiza os dados de um aluno", description = "Substitui o registro inteiro; os responsaveis nao mudam por aqui. Trocar o e-mail troca o login. Responde 409 se o numero de matricula ou o e-mail ja estiverem em uso. Perfis: MASTER, SECRETARIA.")
    public AlunoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AlunoRequest request) {
        return alunoService.atualizar(id, request);
    }

    /** Reenvia o e-mail de primeiro acesso com uma senha provisoria renovada. */
    @PostMapping("/{id}/primeiro-acesso")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Reenvia o e-mail de primeiro acesso", description = "Gera uma nova senha provisoria. Responde 409 se o aluno ja definiu a senha. Perfis: MASTER, SECRETARIA.")
    public ResponseEntity<Void> reenviarAcesso(@PathVariable UUID id) {
        alunoService.reenviarAcesso(id);
        return ResponseEntity.noContent().build();
    }
}
