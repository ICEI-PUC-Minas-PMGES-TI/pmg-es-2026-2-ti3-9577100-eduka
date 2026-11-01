package br.pucminas.eduka.backend.controller;

import java.net.URI;
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

import br.pucminas.eduka.backend.dto.AtualizarAulaRequest;
import br.pucminas.eduka.backend.dto.AulaRequest;
import br.pucminas.eduka.backend.dto.AulaResponse;
import br.pucminas.eduka.backend.dto.AulaResumoResponse;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.AulaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Aulas e chamada das turmas do professor autenticado. Nao ha
 * {@code professorId} nem {@code escolaId} na URL: os dois saem do token, e
 * turma/disciplina em que ele nao esta alocado respondem 404 (ver
 * {@link AulaService}).
 */
@RestController
@RequestMapping("/portal/professor/aulas")
@PreAuthorize(Perfis.PROFESSOR)
@Tag(name = "Aulas e frequencia", description = "Registro das aulas (data e conteudo) e da chamada (presente ou falta) pelo professor autenticado. Perfil: PROFESSOR.")
public class AulaController {

    private final AulaService aulaService;

    public AulaController(AulaService aulaService) {
        this.aulaService = aulaService;
    }

    @GetMapping
    @Operation(summary = "Lista as aulas de uma disciplina na turma, em paginas",
            description = "`pagina` comeca em 0; `tamanho` vai de 1 a 100. Ordenadas por data: `direcao` DESC (padrao, a mais recente primeiro) ou ASC. "
                    + "Cada aula traz so os totais de presentes e faltas; a chamada inteira vem em `GET /{id}`.")
    @ApiResponse(responseCode = "200", description = "A pagina de aulas.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou direcao invalidos.")
    @ApiResponse(responseCode = "404", description = "Turma ou disciplina nao alocada ao professor.")
    public PaginaResponse<AulaResumoResponse> listar(
            @Parameter(description = "Turma") @RequestParam Long turmaId,
            @Parameter(description = "Disciplina") @RequestParam Long disciplinaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = Paginacao.TAMANHO_PADRAO) int tamanho,
            @RequestParam(required = false) Direction direcao) {
        return aulaService.listar(turmaId, disciplinaId, new Paginacao(pagina, tamanho), direcao);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma aula com a chamada", description = "A chamada vem ordenada pelo nome do aluno.")
    @ApiResponse(responseCode = "200", description = "A aula e a chamada.")
    @ApiResponse(responseCode = "404", description = "Aula inexistente, de outra escola ou de turma/disciplina que nao e do professor.")
    public AulaResponse buscar(@PathVariable UUID id) {
        return aulaService.buscar(id);
    }

    @PostMapping
    @Operation(summary = "Registra uma aula com a chamada",
            description = "O professor precisa estar alocado na turma/disciplina; senao, 404. A chamada tem de ser completa: "
                    + "todo aluno com matricula ativa na turma, feita ate o dia da aula, entra na lista, uma vez cada. "
                    + "Quem se matriculou depois do dia da aula nao faz parte da chamada. Tudo ou nada.")
    @ApiResponse(responseCode = "201", description = "Aula registrada.")
    @ApiResponse(responseCode = "400", description = "Data futura; aluno repetido; aluno sem matricula ativa na turma "
            + "ou matriculado depois do dia da aula; ou chamada incompleta (aluno que devia estar na chamada fora da lista).")
    @ApiResponse(responseCode = "404", description = "Turma ou disciplina nao alocada ao professor.")
    public ResponseEntity<AulaResponse> registrar(@Valid @RequestBody AulaRequest request) {
        AulaResponse response = aulaService.registrar(request);
        return ResponseEntity.created(URI.create("/portal/professor/aulas/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corrige o conteudo e a chamada de uma aula",
            description = "Substitui o conteudo e a presenca de cada aluno enviado; turma, disciplina e data nao mudam. "
                    + "Mesmas regras da chamada do registro; quem ja tinha presenca nesta aula e saiu da turma depois pode ser corrigido, mas nao e exigido.")
    @ApiResponse(responseCode = "200", description = "Aula corrigida.")
    @ApiResponse(responseCode = "400", description = "Aluno repetido; aluno sem matricula ativa na turma ou matriculado depois do dia da aula; ou chamada incompleta.")
    @ApiResponse(responseCode = "404", description = "Aula inexistente, de outra escola ou de turma/disciplina que nao e do professor.")
    public AulaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarAulaRequest request) {
        return aulaService.atualizar(id, request);
    }
}
