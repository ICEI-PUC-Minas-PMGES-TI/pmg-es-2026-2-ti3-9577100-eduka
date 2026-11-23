package br.pucminas.eduka.backend.controller.privacidade;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.privacidade.DadosPessoaisAlunoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Direitos do titular sobre os dados do aluno (LGPD, UC39): exportar tudo o
 * que a escola guarda e anonimizar quem saiu. A escola vem do token.
 */
@RestController
@RequestMapping("/alunos")
@Tag(name = "Alunos", description = "Alunos da escola do usuario autenticado.")
public class DadosPessoaisAlunoController {

    private final DadosPessoaisAlunoService dadosPessoaisAlunoService;

    public DadosPessoaisAlunoController(DadosPessoaisAlunoService dadosPessoaisAlunoService) {
        this.dadosPessoaisAlunoService = dadosPessoaisAlunoService;
    }

    @GetMapping(value = "/{id}/dados-pessoais", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Perfis.CADASTRO_ACADEMICO)
    @Operation(summary = "Exporta os dados pessoais do aluno em PDF",
            description = "Relatorio para entregar ao titular (LGPD, art. 18 e 19): dados pessoais, acesso, "
                    + "responsaveis, matriculas, notas publicadas, frequencia, cobrancas e pedidos de correcao. "
                    + "Vem como anexo, sem cache, e entra no registro de atividades. Perfis: MASTER, SECRETARIA.")
    @ApiResponse(responseCode = "200", description = "O PDF.",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Aluno inexistente ou de outra escola.")
    @ApiResponse(responseCode = "409", description = "Aluno anonimizado: nao ha mais dado pessoal.")
    public ResponseEntity<byte[]> exportar(@PathVariable UUID id) {
        DadosPessoaisAlunoService.Arquivo arquivo = dadosPessoaisAlunoService.exportar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(arquivo.conteudo());
    }

    @PostMapping("/{id}/anonimizar")
    @PreAuthorize(Perfis.GESTAO)
    @Operation(summary = "Anonimiza o aluno que saiu da escola",
            description = "Apaga nome, CPF, nascimento, contatos, endereco e genero, desliga o login e desfaz o "
                    + "vinculo com os responsaveis. Cobrancas e pagamentos ficam (historico financeiro exigido "
                    + "por lei); matriculas, notas e frequencia ficam sem identificar o aluno. Nao pode ser "
                    + "desfeito. Perfil: MASTER.")
    @ApiResponse(responseCode = "204", description = "Aluno anonimizado.")
    @ApiResponse(responseCode = "404", description = "Aluno inexistente ou de outra escola.")
    @ApiResponse(responseCode = "409", description = "Ja anonimizado, com matricula ativa ou trancada, ou com "
            + "cobranca em aberto.")
    public ResponseEntity<Void> anonimizar(@PathVariable UUID id) {
        dadosPessoaisAlunoService.anonimizar(id);
        return ResponseEntity.noContent().build();
    }
}
