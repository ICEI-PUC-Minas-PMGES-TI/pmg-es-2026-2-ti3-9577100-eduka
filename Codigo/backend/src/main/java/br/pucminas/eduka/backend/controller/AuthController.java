package br.pucminas.eduka.backend.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.LoginRequest;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.dto.PrimeiroAcessoRequest;
import br.pucminas.eduka.backend.service.AuthService;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.login(), request.senha());
    }

    /** Publica como o login: quem chega aqui ainda nao tem token. */
    @PostMapping("/primeiro-acesso")
    public LoginResponse primeiroAcesso(@Valid @RequestBody PrimeiroAcessoRequest request) {
        return authService.definirSenhaDefinitiva(request.login(), request.senhaProvisoria(), request.novaSenha());
    }
}
