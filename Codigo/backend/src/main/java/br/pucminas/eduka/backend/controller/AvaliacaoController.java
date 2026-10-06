package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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

import br.pucminas.eduka.backend.dto.AtualizarAvaliacaoRequest;
import br.pucminas.eduka.backend.dto.AvaliacaoRequest;
import br.pucminas.eduka.backend.dto.AvaliacaoResponse;
import br.pucminas.eduka.backend.dto.LancarNotasRequest;
import br.pucminas.eduka.backend.dto.NotaAlunoResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.AvaliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Avaliacoes e notas das turmas do professor autenticado. Nao ha
 * {@code professorId} nem {@code escolaId} na URL: os dois saem do token, e
 * turma/disciplina em que ele nao esta alocado respondem 404 (ver
 * {@link AvaliacaoService}).
 */
@RestController
@RequestMapping("/portal/professor/avaliacoes")
@PreAuthorize(Perfis.PROFESSOR)
@Tag(name = "Avaliacoes e notas", description = "Provas, trabalhos e tarefas do professor autenticado, e as notas dos alunos. Perfil: PROFESSOR.")
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    public AvaliacaoController(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    @GetMapping
    @Operation(summary = "Lista as avaliacoes de uma disciplina na turma", description = "Ordenadas por data. Cada uma traz quantas notas ja foram lancadas.")
    public List<AvaliacaoResponse> listar(
            @Parameter(description = "Turma") @RequestParam Long turmaId,
            @Parameter(description = "Disciplina") @RequestParam Long disciplinaId) {
        return avaliacaoService.listar(turmaId, disciplinaId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma avaliacao")
    public AvaliacaoResponse buscar(@PathVariable UUID id) {
        return avaliacaoService.buscar(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra uma avaliacao", description = "O professor precisa estar alocado na turma/disciplina; senao, 404.")
    public ResponseEntity<AvaliacaoResponse> criar(@Valid @RequestBody AvaliacaoRequest request) {
        AvaliacaoResponse response = avaliacaoService.criar(request);
        return ResponseEntity.created(URI.create("/portal/professor/avaliacoes/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma avaliacao", description = "Turma e disciplina nao mudam. Responde 400 se o novo valor fica abaixo de uma nota ja lancada.")
    public AvaliacaoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarAvaliacaoRequest request) {
        return avaliacaoService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma avaliacao", description = "Responde 409 se ja ha nota lancada.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        avaliacaoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/notas")
    @Operation(summary = "Diario de notas da avaliacao", description = "Todo aluno com matricula ativa na turma, com a nota (nula se ainda nao lancada).")
    public List<NotaAlunoResponse> listarNotas(@PathVariable UUID id) {
        return avaliacaoService.listarNotas(id);
    }

    /** Lanca as notas em lote; nota nula apaga a ja lancada. */
    @PutMapping("/{id}/notas")
    @Operation(summary = "Lanca as notas da avaliacao", description = "Em lote, tudo ou nada. Nota nula apaga. Responde 400 para aluno sem matricula ativa na turma ou nota acima do valor da avaliacao.")
    public List<NotaAlunoResponse> lancarNotas(@PathVariable UUID id, @Valid @RequestBody LancarNotasRequest request) {
        return avaliacaoService.lancarNotas(id, request);
    }
}
