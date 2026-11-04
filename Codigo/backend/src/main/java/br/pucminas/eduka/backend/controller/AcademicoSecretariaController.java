package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.CursoRequest;
import br.pucminas.eduka.backend.dto.CursoResponse;
import br.pucminas.eduka.backend.dto.DisciplinaRequest;
import br.pucminas.eduka.backend.dto.DisciplinaResponse;
import br.pucminas.eduka.backend.dto.PeriodoLetivoRequest;
import br.pucminas.eduka.backend.dto.PeriodoLetivoResponse;
import br.pucminas.eduka.backend.dto.TurmaRequest;
import br.pucminas.eduka.backend.dto.TurmaResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.AcademicoSecretariaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Estrutura academica da escola do usuario autenticado. A escola vem do
 * token, nunca da requisicao. Quem trabalha na escola le; so gestao e
 * secretaria cadastram.
 */
@RestController
@RequestMapping("/academico/secretaria")
@PreAuthorize(Perfis.LEITURA_ACADEMICA)
@Tag(name = "Estrutura academica",
        description = "Periodos letivos, cursos, disciplinas e turmas da escola. Cadastro, edicao e exclusao: MASTER, SECRETARIA.")
public class AcademicoSecretariaController {

    private final AcademicoSecretariaService academicoSecretariaService;

    public AcademicoSecretariaController(AcademicoSecretariaService academicoSecretariaService) {
        this.academicoSecretariaService = academicoSecretariaService;
    }

    @GetMapping("/periodos-letivos")
    @Operation(summary = "Lista os periodos letivos")
    public List<PeriodoLetivoResponse> listarPeriodosLetivos(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return academicoSecretariaService.listarPeriodosLetivos(usuario.escolaId());
    }

    @PostMapping("/periodos-letivos")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Cadastra um periodo letivo")
    public ResponseEntity<PeriodoLetivoResponse> cadastrarPeriodoLetivo(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody PeriodoLetivoRequest request) {
        PeriodoLetivoResponse response = academicoSecretariaService.cadastrarPeriodoLetivo(usuario.escolaId(), request);
        return ResponseEntity.created(URI.create("/academico/secretaria/periodos-letivos/" + response.getId()))
                .body(response);
    }

    @PutMapping("/periodos-letivos/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Atualiza um periodo letivo")
    public PeriodoLetivoResponse atualizarPeriodoLetivo(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id, @Valid @RequestBody PeriodoLetivoRequest request) {
        return academicoSecretariaService.atualizarPeriodoLetivo(usuario.escolaId(), id, request);
    }

    @PatchMapping("/periodos-letivos/{id}/encerrar")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Encerra o periodo letivo em andamento", description = "Deixa de receber matricula nova; "
            + "matriculas e notas ja feitas ficam. 400 se o periodo nao esta em andamento.")
    public PeriodoLetivoResponse encerrarPeriodoLetivo(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id) {
        return academicoSecretariaService.encerrarPeriodoLetivo(usuario.escolaId(), id);
    }

    @PatchMapping("/periodos-letivos/{id}/abrir-matriculas")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Abre as matriculas de um periodo planejado", description = "400 se o periodo ja "
            + "comecou ou foi encerrado.")
    public PeriodoLetivoResponse abrirMatriculas(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id) {
        return academicoSecretariaService.abrirMatriculas(usuario.escolaId(), id);
    }

    @DeleteMapping("/periodos-letivos/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Exclui um periodo letivo", description = "Responde 409 se outro cadastro depende dele.")
    public ResponseEntity<Void> excluirPeriodoLetivo(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id) {
        academicoSecretariaService.excluirPeriodoLetivo(usuario.escolaId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cursos")
    @Operation(summary = "Lista os cursos")
    public List<CursoResponse> listarCursos(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return academicoSecretariaService.listarCursos(usuario.escolaId());
    }

    @PostMapping("/cursos")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Cadastra um curso")
    public ResponseEntity<CursoResponse> cadastrarCurso(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody CursoRequest request) {
        CursoResponse response = academicoSecretariaService.cadastrarCurso(usuario.escolaId(), request);
        return ResponseEntity.created(URI.create("/academico/secretaria/cursos/" + response.getId()))
                .body(response);
    }

    @PutMapping("/cursos/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Atualiza um curso")
    public CursoResponse atualizarCurso(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id, @Valid @RequestBody CursoRequest request) {
        return academicoSecretariaService.atualizarCurso(usuario.escolaId(), id, request);
    }

    @DeleteMapping("/cursos/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Exclui um curso", description = "Responde 409 se outro cadastro depende dele.")
    public ResponseEntity<Void> excluirCurso(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id) {
        academicoSecretariaService.excluirCurso(usuario.escolaId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/disciplinas")
    @Operation(summary = "Lista as disciplinas")
    public List<DisciplinaResponse> listarDisciplinas(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return academicoSecretariaService.listarDisciplinas(usuario.escolaId());
    }

    @PostMapping("/disciplinas")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Cadastra uma disciplina", description = "O curso e opcional; se vier, precisa ser da escola.")
    public ResponseEntity<DisciplinaResponse> cadastrarDisciplina(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody DisciplinaRequest request) {
        DisciplinaResponse response = academicoSecretariaService.cadastrarDisciplina(usuario.escolaId(), request);
        return ResponseEntity.created(URI.create("/academico/secretaria/disciplinas/" + response.getId()))
                .body(response);
    }

    @PutMapping("/disciplinas/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Atualiza uma disciplina")
    public DisciplinaResponse atualizarDisciplina(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id, @Valid @RequestBody DisciplinaRequest request) {
        return academicoSecretariaService.atualizarDisciplina(usuario.escolaId(), id, request);
    }

    @DeleteMapping("/disciplinas/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Exclui uma disciplina", description = "Responde 409 se outro cadastro depende dele.")
    public ResponseEntity<Void> excluirDisciplina(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id) {
        academicoSecretariaService.excluirDisciplina(usuario.escolaId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/turmas")
    @Operation(summary = "Lista as turmas")
    public List<TurmaResponse> listarTurmas(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return academicoSecretariaService.listarTurmas(usuario.escolaId());
    }

    @PostMapping("/turmas")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Cadastra uma turma",
            description = "Periodo, curso e disciplinas da grade precisam ser da escola; 404 caso contrario.")
    public ResponseEntity<TurmaResponse> cadastrarTurma(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody TurmaRequest request) {
        TurmaResponse response = academicoSecretariaService.cadastrarTurma(usuario.escolaId(), request);
        return ResponseEntity.created(URI.create("/academico/secretaria/turmas/" + response.getId()))
                .body(response);
    }

    @PutMapping("/turmas/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Atualiza uma turma", description = "Substitui dados e grade. 400 se as vagas ficam abaixo das matriculas ativas; 409 se sai da grade disciplina com avaliacao.")
    public TurmaResponse atualizarTurma(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id, @Valid @RequestBody TurmaRequest request) {
        return academicoSecretariaService.atualizarTurma(usuario.escolaId(), id, request);
    }

    @DeleteMapping("/turmas/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Exclui uma turma", description = "Responde 409 se outro cadastro depende dele.")
    public ResponseEntity<Void> excluirTurma(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id) {
        academicoSecretariaService.excluirTurma(usuario.escolaId(), id);
        return ResponseEntity.noContent().build();
    }
}
