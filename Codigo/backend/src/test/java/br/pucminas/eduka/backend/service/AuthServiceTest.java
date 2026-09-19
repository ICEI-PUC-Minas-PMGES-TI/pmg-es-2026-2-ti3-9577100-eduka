package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SENHA_CORRETA = "senha-correta";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
    }

    private Usuario usuarioAtivo(UUID id, UUID escolaId, String perfil) {
        return new Usuario(id, escolaId, UUID.randomUUID(), "usuario@escola.com",
                passwordEncoder.encode(SENHA_CORRETA), perfil, true, Instant.now());
    }

    @Test
    void efetuaLoginComCredenciaisValidas() {
        UUID userId = UUID.randomUUID();
        UUID escolaId = UUID.randomUUID();
        Usuario usuario = usuarioAtivo(userId, escolaId, "PROFESSOR");
        when(usuarioRepository.findByLogin("usuario@escola.com")).thenReturn(Optional.of(usuario));
        when(jwtService.generateAccessToken(userId, "PROFESSOR", escolaId)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userId)).thenReturn("refresh-token");

        LoginResponse response = authService.login("usuario@escola.com", SENHA_CORRETA);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void rejeitaLoginInexistenteComCredenciaisInvalidas() {
        when(usuarioRepository.findByLogin("desconhecido@escola.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("desconhecido@escola.com", SENHA_CORRETA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("credenciais inválidas");
    }

    @Test
    void rejeitaSenhaIncorretaComCredenciaisInvalidas() {
        Usuario usuario = usuarioAtivo(UUID.randomUUID(), UUID.randomUUID(), "ALUNO");
        when(usuarioRepository.findByLogin("usuario@escola.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login("usuario@escola.com", "senha-errada"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("credenciais inválidas");
    }

    @Test
    void rejeitaUsuarioInativoComProibido() {
        Usuario usuario = new Usuario(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "usuario@escola.com", passwordEncoder.encode(SENHA_CORRETA), "ALUNO", false, Instant.now());
        when(usuarioRepository.findByLogin("usuario@escola.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login("usuario@escola.com", SENHA_CORRETA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("usuário inativo");
    }
}
