package br.pucminas.eduka.backend.controller.academico;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.academico.AtualizarAulaRequest;
import br.pucminas.eduka.backend.dto.academico.AulaRequest;
import br.pucminas.eduka.backend.dto.academico.AulaResponse;
import br.pucminas.eduka.backend.dto.academico.AulaResumoResponse;
import br.pucminas.eduka.backend.dto.academico.ChamadaDoDiaResponse;
import br.pucminas.eduka.backend.dto.academico.MesDeAulasResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.academico.AulaService;
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
                    + "`de` e `ate` (AAAA-MM-DD, inclusive) limitam as datas; sem eles, todas. "
                    + "`busca` filtra pelo conteudo (trecho, sem diferenciar maiusculas). "
                    + "Cada aula traz so os totais de presentes e faltas; a chamada inteira vem em `GET /{id}`.")
    @ApiResponse(responseCode = "200", description = "A pagina de aulas.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho, direcao, datas (`de` depois de `ate`) ou busca invalidos.")
    @ApiResponse(responseCode = "404", description = "Turma ou disciplina nao alocada ao professor.")
    public PaginaResponse<AulaResumoResponse> listar(
            @Parameter(description = "Turma") @RequestParam Long turmaId,
            @Parameter(description = "Disciplina") @RequestParam Long disciplinaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = Paginacao.TAMANHO_PADRAO) int tamanho,
            @RequestParam(required = false) Direction direcao,
            @Parameter(description = "Primeiro dia, AAAA-MM-DD") @RequestParam(required = false) LocalDate de,
            @Parameter(description = "Ultimo dia, AAAA-MM-DD") @RequestParam(required = false) LocalDate ate,
            @Parameter(description = "Trecho do conteudo") @RequestParam(required = false) String busca) {
        return aulaService.listar(turmaId, disciplinaId, de, ate, busca, new Paginacao(pagina, tamanho), direcao);
    }

    @GetMapping("/meses")
    @Operation(summary = "Meses com aula no historico, com os totais",
            description = "Os meses (`AAAA-MM`, o mais recente primeiro) com aula da disciplina na turma, com os mesmos "
                    + "filtros da listagem (`de`, `ate`, `busca`): quantas aulas e o total de presencas e faltas de cada "
                    + "mes. A tela carrega o historico mes a mes e soma os totais para o resumo.")
    @ApiResponse(responseCode = "200", description = "Os meses com aula; vazio sem nenhuma.")
    @ApiResponse(responseCode = "400", description = "Datas (`de` depois de `ate`) ou busca invalidos.")
    @ApiResponse(responseCode = "404", description = "Turma ou disciplina nao alocada ao professor.")
    public List<MesDeAulasResponse> meses(
            @Parameter(description = "Turma") @RequestParam Long turmaId,
            @Parameter(description = "Disciplina") @RequestParam Long disciplinaId,
            @Parameter(description = "Primeiro dia, AAAA-MM-DD") @RequestParam(required = false) LocalDate de,
            @Parameter(description = "Ultimo dia, AAAA-MM-DD") @RequestParam(required = false) LocalDate ate,
            @Parameter(description = "Trecho do conteudo") @RequestParam(required = false) String busca) {
        return aulaService.meses(turmaId, disciplinaId, de, ate, busca);
    }

    @GetMapping("/chamada")
    @Operation(summary = "Prepara a chamada de um dia",
            description = "Traz quem entra na chamada (matricula ativa na turma, feita ate o dia) e as aulas ja registradas nesse dia. "
                    + "Sem `data`, vale hoje no fuso da escola. Nao grava nada.")
    @ApiResponse(responseCode = "200", description = "Os alunos e as aulas do dia.")
    @ApiResponse(responseCode = "400", description = "Data futura ou invalida.")
    @ApiResponse(responseCode = "404", description = "Turma ou disciplina nao alocada ao professor.")
    public ChamadaDoDiaResponse chamadaDoDia(
            @Parameter(description = "Turma") @RequestParam Long turmaId,
            @Parameter(description = "Disciplina") @RequestParam Long disciplinaId,
            @Parameter(description = "Dia da aula, AAAA-MM-DD") @RequestParam(required = false) LocalDate data) {
        return aulaService.chamadaDoDia(turmaId, disciplinaId, data);
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

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma aula com a chamada",
            description = "Apaga a aula e a presenca de cada aluno nela, tudo ou nada. Serve para desfazer uma aula registrada com data errada "
                    + "ou em duplicidade (a data nao e editavel); as faltas dela deixam de contar no boletim.")
    @ApiResponse(responseCode = "204", description = "Aula excluida.")
    @ApiResponse(responseCode = "404", description = "Aula inexistente, de outra escola ou de turma/disciplina que nao e do professor.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        aulaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
