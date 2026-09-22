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

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SENHA = "senha-forte-123";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
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
    void loginReturnsTokensWhenPasswordMatches() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));
        when(jwtService.generateAccessToken(usuario.getId(), "FINANCEIRO", usuario.getEscolaId()))
                .thenReturn("access-token");
        when(jwtService.generateRefreshToken(usuario.getId())).thenReturn("refresh-token");

        LoginResponse response = authService.login("maria", SENHA);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.usuarioId()).isEqualTo(usuario.getId());
        assertThat(response.perfil()).isEqualTo(PerfilUsuario.FINANCEIRO);
    }

    @Test
    void loginComSuperAdminSemEscolaAssociada() {
        Usuario superAdmin = Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(null)
                .pessoaId(UUID.randomUUID())
                .login("admin")
                .senhaHash(passwordEncoder.encode(SENHA))
                .perfil(PerfilUsuario.SUPER_ADMIN)
                .ativo(true)
                .build();
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.of(superAdmin));
        when(jwtService.generateAccessToken(superAdmin.getId(), "SUPER_ADMIN", null)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(superAdmin.getId())).thenReturn("refresh-token");

        LoginResponse response = authService.login("admin", SENHA);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginNormalizesLoginBeforeLookup() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        authService.login("  MARIA ", SENHA);

        verify(usuarioRepository).findByLogin("maria");
    }

    @Test
    void loginRejectsWrongPassword() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login("maria", "errada"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(jwtService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void loginRejectsPlainHashAsPassword() {
        // Garante que a comparacao nao e "senha == coluna": enviar o proprio hash
        // como senha nao pode funcionar.
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login("maria", usuario.getSenhaHash()))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void loginRejectsUnknownLoginWithSameException() {
        when(usuarioRepository.findByLogin("ninguem")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ninguem", SENHA))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage(new CredenciaisInvalidasException().getMessage());
    }

    @Test
    void loginRejectsInactiveUserEvenWithCorrectPassword() {
        usuario.setAtivo(false);
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login("maria", SENHA))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage(new CredenciaisInvalidasException().getMessage());

        verify(jwtService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void loginDoesNotRewriteHashWhenAlreadyCurrent() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        authService.login("maria", SENHA);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void loginUpgradesLegacyHashOnSuccessfulLogin() {
        // Senha gravada com custo 10: login deve funcionar e o hash ser regravado com 12.
        String hashAntigo = "{bcrypt}" + new BCryptPasswordEncoder(10).encode(SENHA);
        usuario.setSenhaHash(hashAntigo);
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        authService.login("maria", SENHA);

        assertThat(usuario.getSenhaHash()).isNotEqualTo(hashAntigo);
        assertThat(usuario.getSenhaHash()).startsWith("{bcrypt}$2a$12$");
        assertThat(passwordEncoder.matches(SENHA, usuario.getSenhaHash())).isTrue();
        verify(usuarioRepository).save(usuario);
    }
}
