package br.pucminas.eduka.backend.controller.acesso;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.acesso.RecuperacaoSenhaResponse;
import br.pucminas.eduka.backend.dto.acesso.RedefinirSenhaRequest;
import br.pucminas.eduka.backend.dto.acesso.SolicitarRecuperacaoSenhaRequest;
import br.pucminas.eduka.backend.service.acesso.RecuperacaoSenhaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Publicas como o login: quem esqueceu a senha nao tem token. */
@RestController
@Tag(name = "Autenticacao", description = "Login, codigo de verificacao e troca da senha provisoria. Rotas publicas.")
@SecurityRequirements
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoSenhaService;

    public RecuperacaoSenhaController(RecuperacaoSenhaService recuperacaoSenhaService) {
        this.recuperacaoSenhaService = recuperacaoSenhaService;
    }

    @PostMapping("/senha/recuperacao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envia por e-mail um codigo para redefinir a senha",
            description = "Responde 202 com o mesmo corpo para qualquer login, exista ou nao, para nao revelar "
                    + "quais estao cadastrados. Pedidos repetidos antes do intervalo minimo, ou alem do limite "
                    + "de envios, nao enviam outro codigo.")
    public RecuperacaoSenhaResponse solicitar(@Valid @RequestBody SolicitarRecuperacaoSenhaRequest request) {
        return recuperacaoSenhaService.solicitar(request.login());
    }

    @PostMapping("/senha/redefinicao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Define uma senha nova com o codigo recebido por e-mail",
            description = "Responde 204 e a nova senha ja vale para o login. Responde 400 para senha fora das "
                    + "regras e, com a mesma mensagem em todos os casos, para codigo errado, expirado ou "
                    + "login desconhecido.")
    public void redefinir(@Valid @RequestBody RedefinirSenhaRequest request) {
        recuperacaoSenhaService.redefinir(request.login(), request.codigo(), request.novaSenha());
    }
}
