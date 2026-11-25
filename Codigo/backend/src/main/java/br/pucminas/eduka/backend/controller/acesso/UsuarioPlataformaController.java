package br.pucminas.eduka.backend.controller.acesso;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.dto.acesso.UsuarioPlataformaResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.acesso.UsuarioPlataformaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Logins da plataforma, para a tela Usuarios do SUPER_ADMIN (S5-14).
 *
 * <p>
 * O gate de modulo em {@code RotasPorPerfil} libera {@code /usuarios/**}
 * tambem para MASTER e SECRETARIA (equipe); estas rotas sao so do
 * SUPER_ADMIN, por isso o {@link Perfis#SUPER_ADMIN} aqui em cima.
 * </p>
 */
@RestController
@RequestMapping("/usuarios/plataforma")
@PreAuthorize(Perfis.SUPER_ADMIN)
@Tag(name = "Usuarios da plataforma", description = "Logins de todas as escolas; o SUPER_ADMIN gere o do MASTER. "
        + "Perfil: SUPER_ADMIN.")
public class UsuarioPlataformaController {

    private final UsuarioPlataformaService usuarioPlataformaService;

    public UsuarioPlataformaController(UsuarioPlataformaService usuarioPlataformaService) {
        this.usuarioPlataformaService = usuarioPlataformaService;
    }

    @GetMapping
    @Operation(summary = "Lista os logins em paginas", description = "Em ordem de login. Filtros opcionais por "
            + "escola, perfil, situacao (`ativo`) e trecho do login (`busca`). `pagina` comeca em 0; `tamanho` vai "
            + "de 1 a 100. Sem nome nem CPF: os dados pessoais ficam com a escola.")
    @ApiResponse(responseCode = "200", description = "A pagina de logins.")
    @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou filtro invalidos.")
    public PaginaResponse<UsuarioPlataformaResponse> listar(
            @Parameter(description = "Escola") @RequestParam(required = false) UUID escolaId,
            @Parameter(description = "Perfil do login") @RequestParam(required = false) PerfilUsuario perfil,
            @Parameter(description = "Ativos (true) ou inativos (false)") @RequestParam(required = false) Boolean ativo,
            @Parameter(description = "Trecho do login (e-mail)") @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = Paginacao.TAMANHO_PADRAO) int tamanho) {
        return usuarioPlataformaService.listar(escolaId, perfil, ativo, busca, new Paginacao(pagina, tamanho));
    }

    @PatchMapping("/{id}/inativar")
    @Operation(summary = "Inativa o login de um MASTER", description = "Ele deixa de entrar e libera a vaga no "
            + "limite de usuarios da escola. Login de outro perfil responde 404: e gerido pela escola.")
    @ApiResponse(responseCode = "200", description = "O login inativado.")
    @ApiResponse(responseCode = "404", description = "Login inexistente ou que nao e de MASTER.")
    public UsuarioPlataformaResponse inativar(@PathVariable UUID id) {
        return usuarioPlataformaService.inativar(id);
    }

    @PatchMapping("/{id}/ativar")
    @Operation(summary = "Reativa o login de um MASTER", description = "Conta no limite de usuarios da escola.")
    @ApiResponse(responseCode = "200", description = "O login reativado.")
    @ApiResponse(responseCode = "404", description = "Login inexistente ou que nao e de MASTER.")
    @ApiResponse(responseCode = "409", description = "Escola no limite de usuarios (LIMITE_USUARIOS_ATINGIDO).")
    public UsuarioPlataformaResponse ativar(@PathVariable UUID id) {
        return usuarioPlataformaService.ativar(id);
    }

    @PostMapping("/{id}/primeiro-acesso")
    @Operation(summary = "Reenvia o primeiro acesso de um MASTER", description = "Gera nova senha provisoria (o "
            + "CPF) e manda o e-mail de novo. So para quem ainda nao definiu a senha.")
    @ApiResponse(responseCode = "200", description = "O login, aguardando o novo primeiro acesso.")
    @ApiResponse(responseCode = "400", description = "Login inativo.")
    @ApiResponse(responseCode = "404", description = "Login inexistente ou que nao e de MASTER.")
    @ApiResponse(responseCode = "409", description = "O MASTER ja definiu a senha definitiva.")
    public UsuarioPlataformaResponse reenviarAcesso(@PathVariable UUID id) {
        return usuarioPlataformaService.reenviarAcesso(id);
    }
}
