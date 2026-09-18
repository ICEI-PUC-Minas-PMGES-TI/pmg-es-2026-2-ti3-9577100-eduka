package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.dto.LoginRequest;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.model.PerfilUsuario;
import br.pucminas.eduka.backend.model.Usuario;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {

    private static final String SENHA = "senha-forte-123";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private AutenticacaoService autenticacaoService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        autenticacaoService = new AutenticacaoService(usuarioRepository, passwordEncoder, jwtService);
        usuario = Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(UUID.randomUUID())
                .pessoaId(UUID.randomUUID())
                .login("maria")
                .senhaHash(passwordEncoder.encode(SENHA))
                .perfil(PerfilUsuario.FINANCEIRO)
                .ativo(true)
                .build();
    }

    @Test
    void autenticarReturnsTokensWhenPasswordMatches() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));
        when(jwtService.generateAccessToken(usuario.getId(), "FINANCEIRO", usuario.getEscolaId()))
                .thenReturn("access-token");
        when(jwtService.generateRefreshToken(usuario.getId())).thenReturn("refresh-token");

        LoginResponse response = autenticacaoService.autenticar(new LoginRequest("maria", SENHA));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.usuarioId()).isEqualTo(usuario.getId());
        assertThat(response.perfil()).isEqualTo(PerfilUsuario.FINANCEIRO);
    }

    @Test
    void autenticarNormalizesLoginBeforeLookup() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        autenticacaoService.autenticar(new LoginRequest("  MARIA ", SENHA));

        verify(usuarioRepository).findByLogin("maria");
    }

    @Test
    void autenticarRejectsWrongPassword() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> autenticacaoService.autenticar(new LoginRequest("maria", "errada")))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(jwtService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void autenticarRejectsPlainHashAsPassword() {
        // Garante que a comparacao nao e "senha == coluna": enviar o proprio hash
        // como senha nao pode funcionar.
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> autenticacaoService
                .autenticar(new LoginRequest("maria", usuario.getSenhaHash())))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void autenticarRejectsUnknownLoginWithSameException() {
        when(usuarioRepository.findByLogin("ninguem")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autenticacaoService.autenticar(new LoginRequest("ninguem", SENHA)))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage(new CredenciaisInvalidasException().getMessage());
    }

    @Test
    void autenticarRejectsInactiveUserEvenWithCorrectPassword() {
        usuario.setAtivo(false);
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> autenticacaoService.autenticar(new LoginRequest("maria", SENHA)))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage(new CredenciaisInvalidasException().getMessage());

        verify(jwtService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void autenticarDoesNotRewriteHashWhenAlreadyCurrent() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        autenticacaoService.autenticar(new LoginRequest("maria", SENHA));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void autenticarUpgradesLegacyHashOnSuccessfulLogin() {
        // Senha gravada com custo 10: login deve funcionar e o hash ser regravado com 12.
        String hashAntigo = "{bcrypt}" + new BCryptPasswordEncoder(10).encode(SENHA);
        usuario.setSenhaHash(hashAntigo);
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        autenticacaoService.autenticar(new LoginRequest("maria", SENHA));

        assertThat(usuario.getSenhaHash()).isNotEqualTo(hashAntigo);
        assertThat(usuario.getSenhaHash()).startsWith("{bcrypt}$2a$12$");
        assertThat(passwordEncoder.matches(SENHA, usuario.getSenhaHash())).isTrue();
        verify(usuarioRepository).save(usuario);
    }
}
