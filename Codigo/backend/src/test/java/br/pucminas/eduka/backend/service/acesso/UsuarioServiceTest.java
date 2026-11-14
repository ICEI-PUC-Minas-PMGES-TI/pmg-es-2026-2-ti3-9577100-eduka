package br.pucminas.eduka.backend.service.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.dto.acesso.CadastroUsuarioRequest;
import br.pucminas.eduka.backend.exception.acesso.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.exception.acesso.LoginJaCadastradoException;
import br.pucminas.eduka.backend.exception.acesso.SenhaInvalidaException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;
import br.pucminas.eduka.backend.security.PerfilNaoPermitidoException;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final String SENHA = "senha-forte-123";
    private static final UUID ESCOLA_DO_TOKEN = UUID.randomUUID();

    @Mock
    private UsuarioRepository usuarioRepository;

    // Encoder real: o ponto do teste e garantir que o hash e gerado de verdade.
    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder, new IsolamentoEscola());
        // Padrao dos testes de senha/login: um MASTER cadastrando uma secretaria.
        autenticarComo("MASTER", ESCOLA_DO_TOKEN);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarComo(String perfil, UUID escolaId) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), perfil, escolaId);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(usuario, null, "ROLE_" + perfil));
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

        assertThat(salvo.getEscolaId()).isEqualTo(ESCOLA_DO_TOKEN);
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

    @Nested
    class HierarquiaDePerfis {

        @ParameterizedTest(name = "{0} cria {1}")
        @CsvSource({
                "SUPER_ADMIN, MASTER",
                "SUPER_ADMIN, ALUNO",
                "MASTER, SECRETARIA",
                "MASTER, FINANCEIRO",
                "MASTER, PROFESSOR",
                "MASTER, ALUNO",
                "SECRETARIA, PROFESSOR",
                "SECRETARIA, ALUNO" })
        void perfilPermitido(String criador, PerfilUsuario novo) {
            autenticarComo(criador, ESCOLA_DO_TOKEN);
            when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

            Usuario salvo = usuarioService.cadastrar(request("maria", SENHA, novo));

            assertThat(salvo.getPerfil()).isEqualTo(novo);
        }

        @ParameterizedTest(name = "{0} nao cria {1}")
        @CsvSource({
                "SUPER_ADMIN, SUPER_ADMIN",
                "MASTER, SUPER_ADMIN",
                "MASTER, MASTER",
                "SECRETARIA, MASTER",
                "SECRETARIA, SECRETARIA",
                "SECRETARIA, FINANCEIRO",
                "FINANCEIRO, ALUNO",
                "PROFESSOR, ALUNO",
                "ALUNO, ALUNO" })
        void perfilNegado(String criador, PerfilUsuario novo) {
            autenticarComo(criador, ESCOLA_DO_TOKEN);

            assertThatThrownBy(() -> usuarioService.cadastrar(request("maria", SENHA, novo)))
                    .isInstanceOf(PerfilNaoPermitidoException.class);

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void naoSuperAdminSempreCadastraNaPropriaEscola() {
            autenticarComo("SECRETARIA", ESCOLA_DO_TOKEN);
            when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
            UUID outraEscola = UUID.randomUUID();

            Usuario salvo = usuarioService.cadastrar(new CadastroUsuarioRequest(
                    outraEscola, UUID.randomUUID(), "maria", SENHA, PerfilUsuario.ALUNO));

            assertThat(salvo.getEscolaId()).isEqualTo(ESCOLA_DO_TOKEN);
        }

        @Test
        void superAdminCadastraNaEscolaDoRequest() {
            autenticarComo("SUPER_ADMIN", null);
            when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
            UUID escolaNova = UUID.randomUUID();

            Usuario salvo = usuarioService.cadastrar(new CadastroUsuarioRequest(
                    escolaNova, UUID.randomUUID(), "maria", SENHA, PerfilUsuario.MASTER));

            assertThat(salvo.getEscolaId()).isEqualTo(escolaNova);
        }

        @Test
        void superAdminPrecisaInformarEscola() {
            autenticarComo("SUPER_ADMIN", null);

            assertThatThrownBy(() -> usuarioService.cadastrar(new CadastroUsuarioRequest(
                    null, UUID.randomUUID(), "maria", SENHA, PerfilUsuario.MASTER)))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void perfilObrigatorio() {
            assertThatThrownBy(() -> usuarioService.cadastrar(request("maria", SENHA, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static CadastroUsuarioRequest request(String login, String senha) {
        return request(login, senha, PerfilUsuario.SECRETARIA);
    }

    private static CadastroUsuarioRequest request(String login, String senha, PerfilUsuario perfil) {
        return new CadastroUsuarioRequest(UUID.randomUUID(), UUID.randomUUID(), login, senha, perfil);
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
