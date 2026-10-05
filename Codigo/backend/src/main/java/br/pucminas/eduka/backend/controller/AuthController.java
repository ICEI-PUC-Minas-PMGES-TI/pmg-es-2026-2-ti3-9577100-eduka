package br.pucminas.eduka.backend.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.LoginRequest;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.dto.PrimeiroAcessoRequest;
import br.pucminas.eduka.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Autenticacao", description = "Login e troca da senha provisoria. Rotas publicas.")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica o usuario",
            description = "Devolve access e refresh token. Se `trocaSenhaObrigatoria` vier verdadeiro, "
                    + "os tokens vem nulos e e preciso chamar `POST /primeiro-acesso` antes.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.login(), request.senha());
    }

    /** Publica como o login: quem chega aqui ainda nao tem token. */
    @PostMapping("/primeiro-acesso")
    @Operation(summary = "Troca a senha provisoria pela definitiva",
            description = "Usado no primeiro acesso. Devolve os tokens ja autenticados.")
    public LoginResponse primeiroAcesso(@Valid @RequestBody PrimeiroAcessoRequest request) {
        return authService.definirSenhaDefinitiva(request.login(), request.senhaProvisoria(), request.novaSenha());
    }
}
