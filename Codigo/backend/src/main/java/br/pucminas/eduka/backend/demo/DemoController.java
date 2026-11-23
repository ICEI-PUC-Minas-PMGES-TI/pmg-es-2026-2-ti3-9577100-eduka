package br.pucminas.eduka.backend.demo;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.acesso.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Tela /login/demo do ambiente de apresentacao (ver {@link DemoAcessoService}).
 * Rotas publicas como o login; fora do perfil demo respondem 404.
 */
@RestController
@Profile("demo")
@Tag(name = "Demonstracao", description = "Acesso de um clique as contas de teste, so no ambiente de apresentacao.")
@SecurityRequirements
public class DemoController {

    public record AcessoRequest(@NotBlank String login, @NotBlank String senha) {
    }

    public record EntrarRequest(@NotBlank String login, @NotBlank String senha, @NotBlank String conta) {
    }

    private final DemoAcessoService demoAcessoService;

    public DemoController(DemoAcessoService demoAcessoService) {
        this.demoAcessoService = demoAcessoService;
    }

    @PostMapping("/demo/contas")
    @Operation(summary = "Lista as contas de teste", description = "401 se o login ou a senha do acesso de "
            + "demonstracao nao conferem.")
    public List<DemoAcessoService.Grupo> contas(@Valid @RequestBody AcessoRequest request) {
        return demoAcessoService.contas(request.login(), request.senha());
    }

    @PostMapping("/demo/entrar")
    @Operation(summary = "Entra em uma conta de teste", description = "Devolve os tokens da conta, como o "
            + "POST /login. 401 se o acesso nao confere ou a conta nao e de teste.")
    public LoginResponse entrar(@Valid @RequestBody EntrarRequest request) {
        return demoAcessoService.entrar(request.login(), request.senha(), request.conta());
    }
}
