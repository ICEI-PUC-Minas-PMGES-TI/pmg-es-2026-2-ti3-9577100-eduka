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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.dto.CadastroUsuarioRequest;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.exception.LoginJaCadastradoException;
import br.pucminas.eduka.backend.exception.SenhaInvalidaException;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final String SENHA = "senha-forte-123";

    @Mock
    private UsuarioRepository usuarioRepository;

    // Encoder real: o ponto do teste e garantir que o hash e gerado de verdade.
    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);
    }

    @Test
    void cadastrarPersistsHashInsteadOfPlainPassword() {
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.cadastrar(request("maria", SENHA));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        String senhaHash = captor.getValue().getSenhaHash();

        assertThat(senhaHash).isNotEqualTo(SENHA);
        assertThat(senhaHash).startsWith("{bcrypt}$2a$12$");
        assertThat(passwordEncoder.matches(SENHA, senhaHash)).isTrue();
    }

    @Test
    void cadastrarCopiesRequestFieldsAndStartsActive() {
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        CadastroUsuarioRequest request = request("maria", SENHA);

        Usuario salvo = usuarioService.cadastrar(request);

        assertThat(salvo.getEscolaId()).isEqualTo(request.escolaId());
        assertThat(salvo.getPessoaId()).isEqualTo(request.pessoaId());
        assertThat(salvo.getLogin()).isEqualTo("maria");
        assertThat(salvo.getPerfil()).isEqualTo(PerfilUsuario.SECRETARIA);
        assertThat(salvo.isAtivo()).isTrue();
    }

    @Test
    void cadastrarNormalizesLoginBeforeCheckingAndSaving() {
        when(usuarioRepository.existsByLogin("maria@escola.com")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario salvo = usuarioService.cadastrar(request("  Maria@Escola.COM ", SENHA));

        assertThat(salvo.getLogin()).isEqualTo("maria@escola.com");
        verify(usuarioRepository).existsByLogin("maria@escola.com");
    }

    @Test
    void cadastrarRejectsDuplicateLogin() {
        when(usuarioRepository.existsByLogin("maria")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.cadastrar(request("maria", SENHA)))
                .isInstanceOf(LoginJaCadastradoException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastrarRejectsShortPassword() {
        assertThatThrownBy(() -> usuarioService.cadastrar(request("maria", "1234567")))
                .isInstanceOf(SenhaInvalidaException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastrarRejectsPasswordLongerThanBcryptLimit() {
        // 73 bytes: o BCrypt silenciosamente ignoraria o ultimo.
        String senha73 = "a".repeat(73);

        assertThatThrownBy(() -> usuarioService.cadastrar(request("maria", senha73)))
                .isInstanceOf(SenhaInvalidaException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastrarCountsBytesNotCharsForBcryptLimit() {
        // "é" ocupa 2 bytes em UTF-8: 36 chars = 72 bytes (ok), 37 = 74 (excede).
        String senha72Bytes = "é".repeat(36);
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.cadastrar(request("maria", senha72Bytes));

        assertThatThrownBy(() -> usuarioService.cadastrar(request("maria", "é".repeat(37))))
                .isInstanceOf(SenhaInvalidaException.class);
    }

    @Test
    void cadastrarRejectsBlankLogin() {
        assertThatThrownBy(() -> usuarioService.cadastrar(request("   ", SENHA)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alterarSenhaReplacesHashWhenCurrentPasswordMatches() {
        Usuario usuario = usuarioComSenha(SENHA);
        String hashAntigo = usuario.getSenhaHash();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        usuarioService.alterarSenha(usuario.getId(), SENHA, "nova-senha-456");

        assertThat(usuario.getSenhaHash()).isNotEqualTo(hashAntigo);
        assertThat(passwordEncoder.matches("nova-senha-456", usuario.getSenhaHash())).isTrue();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void alterarSenhaRejectsWrongCurrentPassword() {
        Usuario usuario = usuarioComSenha(SENHA);
        String hashAntigo = usuario.getSenhaHash();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> usuarioService.alterarSenha(usuario.getId(), "errada", "nova-senha-456"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(usuario.getSenhaHash()).isEqualTo(hashAntigo);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void alterarSenhaValidatesNewPasswordOnlyAfterCurrentIsConfirmed() {
        Usuario usuario = usuarioComSenha(SENHA);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> usuarioService.alterarSenha(usuario.getId(), SENHA, "curta"))
                .isInstanceOf(SenhaInvalidaException.class);

        verify(usuarioRepository, never()).save(any());
    }

    private static CadastroUsuarioRequest request(String login, String senha) {
        return new CadastroUsuarioRequest(
                UUID.randomUUID(), UUID.randomUUID(), login, senha, PerfilUsuario.SECRETARIA);
    }

    private Usuario usuarioComSenha(String senha) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(UUID.randomUUID())
                .pessoaId(UUID.randomUUID())
                .login("maria")
                .senhaHash(passwordEncoder.encode(senha))
                .perfil(PerfilUsuario.SECRETARIA)
                .build();
    }
}
