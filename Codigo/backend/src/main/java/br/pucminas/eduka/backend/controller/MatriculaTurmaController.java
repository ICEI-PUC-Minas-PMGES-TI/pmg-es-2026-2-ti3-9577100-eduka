package br.pucminas.eduka.backend.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.dto.AtualizarMatriculaRequest;
import br.pucminas.eduka.backend.dto.MatriculaTurmaRequest;
import br.pucminas.eduka.backend.dto.MatriculaTurmaResponse;
import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.MatriculaTurmaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Matricula de alunos nas turmas da escola do usuario autenticado. Perfis
 * administrativos consultam; so gestao e secretaria matriculam e alteram. O
 * professor ve os alunos das proprias turmas pelo portal.
 */
@RestController
@RequestMapping("/matriculas")
@Tag(name = "Matriculas",
        description = "Alunos nas turmas. Leitura: MASTER, SECRETARIA, FINANCEIRO. "
                + "Cadastro e alteracao: MASTER, SECRETARIA.")
public class MatriculaTurmaController {

    private final MatriculaTurmaService matriculaService;

    public MatriculaTurmaController(MatriculaTurmaService matriculaService) {
        this.matriculaService = matriculaService;
    }

    @GetMapping
    @PreAuthorize(Perfis.LEITURA_ALUNOS)
    @Operation(summary = "Lista as matriculas", description = "Filtros opcionais por turma, aluno e situacao.")
    public List<MatriculaTurmaResponse> listar(@RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) UUID alunoId,
            @RequestParam(required = false) StatusMatricula status) {
        return matriculaService.listar(turmaId, alunoId, status);
    }

    @GetMapping("/pagina")
    @PreAuthorize(Perfis.LEITURA_ALUNOS)
    @Operation(summary = "Lista as matriculas em paginas",
            description = "Mesmos filtros da listagem. `pagina` comeca em 0; `tamanho` vai de 1 a 100. "
                    + "`ordenarPor`: ALUNO (padrao, pelo nome) ou DATA_MATRICULA; `direcao`: ASC (padrao) ou DESC.")
    @ApiResponse(responseCode = "200", description = "A pagina de matriculas.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou ordenacao invalidos.")
    public PaginaResponse<MatriculaTurmaResponse> listarPaginado(@RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) UUID alunoId,
            @RequestParam(required = false) StatusMatricula status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = Paginacao.TAMANHO_PADRAO) int tamanho,
            @RequestParam(defaultValue = "ALUNO") Ordenacao.Matriculas ordenarPor,
            @RequestParam(required = false) Direction direcao) {
        return matriculaService.listarPaginado(turmaId, alunoId, status, new Paginacao(pagina, tamanho), ordenarPor,
                direcao);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.LEITURA_ALUNOS)
    @Operation(summary = "Busca uma matricula")
    public MatriculaTurmaResponse buscar(@PathVariable UUID id) {
        return matriculaService.buscar(id);
    }

    @PostMapping
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Matricula um aluno numa turma",
            description = "Cria a matricula como ATIVA. 404 se aluno ou turma nao forem da escola; 400 se a "
                    + "turma estiver inativa; 409 se o aluno ja tiver matricula na turma ou se nao houver vaga.")
    public ResponseEntity<MatriculaTurmaResponse> matricular(@Valid @RequestBody MatriculaTurmaRequest request) {
        MatriculaTurmaResponse response = matriculaService.matricular(request);
        return ResponseEntity.created(URI.create("/matriculas/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Atualiza uma matricula",
            description = "Muda a situacao (ATIVA <-> TRANCADA; ATIVA/TRANCADA -> CANCELADA; ATIVA -> CONCLUIDA), "
                    + "transfere de turma (`turmaId`, so para matricula ativa) e/ou troca a observacao. 400 para "
                    + "transicao invalida; 409 se a turma de destino estiver cheia ou ja tiver o aluno.")
    public MatriculaTurmaResponse atualizar(@PathVariable UUID id,
            @Valid @RequestBody AtualizarMatriculaRequest request) {
        return matriculaService.atualizar(id, request);
    }
}
