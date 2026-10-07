package br.pucminas.eduka.backend.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.LoginRequest;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.dto.PrimeiroAcessoRequest;
import br.pucminas.eduka.backend.dto.ReenviarCodigoRequest;
import br.pucminas.eduka.backend.dto.VerificacaoLoginResponse;
import br.pucminas.eduka.backend.dto.VerificarCodigoRequest;
import br.pucminas.eduka.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Autenticacao", description = "Login, codigo de verificacao e troca da senha provisoria. Rotas publicas.")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica o usuario",
            description = "Devolve access e refresh token. Se `trocaSenhaObrigatoria` vier verdadeiro, "
                    + "os tokens vem nulos e e preciso chamar `POST /primeiro-acesso` antes. Se `verificacao` "
                    + "vier preenchida, os tokens vem nulos e saem em `POST /login/codigo`, com o codigo "
                    + "enviado por e-mail.")
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

    /** Publica como o login: o token so existe depois desta etapa. */
    @PostMapping("/login/codigo")
    @Operation(summary = "Confirma o codigo de verificacao do login",
            description = "Segunda etapa do login. Devolve os tokens. Responde 401 para codigo errado, "
                    + "410 para codigo expirado (peca o reenvio) e 404 quando a verificacao foi encerrada "
                    + "e e preciso fazer o login de novo.")
    public LoginResponse confirmarCodigo(@Valid @RequestBody VerificarCodigoRequest request) {
        return authService.confirmarCodigo(request.verificacaoId(), request.codigo());
    }

    @PostMapping("/login/codigo/reenvio")
    @Operation(summary = "Envia um novo codigo de verificacao",
            description = "Invalida o codigo anterior. Responde 429 antes do intervalo minimo entre envios "
                    + "ou depois do limite de envios.")
    public VerificacaoLoginResponse reenviarCodigo(@Valid @RequestBody ReenviarCodigoRequest request) {
        return authService.reenviarCodigo(request.verificacaoId());
    }
}
