package br.pucminas.eduka.backend.security;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Existe so em src/test: alvo para provar o filter chain, o @PreAuthorize e o
 * isolamento por escola enquanto nao ha controller de negocio. Um controller
 * real so precisa repetir o mesmo padrao para herdar a cobertura.
 */
@RestController
class AutorizacaoTestController {

    private final IsolamentoEscola isolamentoEscola;

    AutorizacaoTestController(IsolamentoEscola isolamentoEscola) {
        this.isolamentoEscola = isolamentoEscola;
    }

    @GetMapping("/teste/autenticado")
    Map<String, Object> autenticado(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return Map.of(
                "id", usuario.id().toString(),
                "perfil", usuario.perfil(),
                "escolaId", usuario.escolaId().toString());
    }

    @GetMapping("/teste/secretaria")
    @PreAuthorize("hasRole('SECRETARIA')")
    String somenteSecretaria() {
        return "ok";
    }

    @GetMapping("/teste/gestao")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MASTER')")
    String somenteGestao() {
        return "ok";
    }

    @GetMapping("/teste/escolas/{escolaId}/recurso")
    String recursoDaEscola(@PathVariable UUID escolaId) {
        isolamentoEscola.exigirAcesso(escolaId);
        return "ok";
    }

    @GetMapping("/teste/erro")
    String erroInterno() {
        throw new IllegalStateException("detalhe interno que nao pode vazar");
    }
}
