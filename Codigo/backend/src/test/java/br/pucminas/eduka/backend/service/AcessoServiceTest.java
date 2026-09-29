package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.exception.AcessoJaAtivadoException;
import br.pucminas.eduka.backend.exception.LoginJaCadastradoException;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;
import br.pucminas.eduka.backend.security.PerfilNaoPermitidoException;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Primeiro acesso: login = e-mail, senha provisoria = CPF (so o hash), com
 * validade, e o e-mail disparado por evento sem carregar o CPF.
 */
@ExtendWith(MockitoExtension.class)
class AcessoServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final String CPF = "52998224725";
    private static final Instant AGORA = Instant.parse("2026-09-28T12:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ApplicationEventPublisher eventos;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private AcessoService acessoService;

    @BeforeEach
    void setUp() {
        acessoService = new AcessoService(usuarioRepository, passwordEncoder, new IsolamentoEscola(), eventos,
                Clock.fixed(AGORA, ZoneOffset.UTC), 7);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarComo(String perfil) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), perfil, ESCOLA);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(usuario, null, "ROLE_" + perfil));
    }

    private static Pessoa pessoa() {
        return Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA).nome("Ana Souza").cpf(CPF)
                .email(" Ana.Souza@Escola.com ").ativo(true).build();
    }

    private void stubSave() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario usuario = inv.getArgument(0);
            if (usuario.getId() == null) {
                usuario.setId(UUID.randomUUID());
            }
            return usuario;
        });
    }

    @Test
    void criaUsuarioComLoginPeloEmailESenhaProvisoriaDoCpf() {
        autenticarComo("SECRETARIA");
        Pessoa pessoa = pessoa();
        stubSave();

        Usuario usuario = acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.PROFESSOR);

        assertThat(usuario.getLogin()).isEqualTo("ana.souza@escola.com");
        assertThat(usuario.getPessoaId()).isEqualTo(pessoa.getId());
        assertThat(usuario.getEscolaId()).isEqualTo(ESCOLA);
        assertThat(usuario.getPerfil()).isEqualTo(PerfilUsuario.PROFESSOR);
        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.isSenhaProvisoria()).isTrue();
        assertThat(usuario.getSenhaProvisoriaExpiraEm()).isEqualTo(AGORA.plus(Duration.ofDays(7)));
        assertThat(usuario.getSenhaHash()).doesNotContain(CPF);
        assertThat(passwordEncoder.matches(CPF, usuario.getSenhaHash())).isTrue();
    }

    @Test
    void publicaEventoDoEmailSemOCpf() {
        autenticarComo("SECRETARIA");
        stubSave();

        Usuario usuario = acessoService.enviarAcessoInicial(pessoa(), PerfilUsuario.PROFESSOR);

        ArgumentCaptor<AcessoInicialCriado> captor = ArgumentCaptor.forClass(AcessoInicialCriado.class);
        verify(eventos).publishEvent(captor.capture());
        AcessoInicialCriado evento = captor.getValue();
        assertThat(evento.usuarioId()).isEqualTo(usuario.getId());
        assertThat(evento.email()).isEqualTo("Ana.Souza@Escola.com");
        assertThat(evento.login()).isEqualTo("ana.souza@escola.com");
        assertThat(evento.nome()).isEqualTo("Ana Souza");
        assertThat(evento.expiraEm()).isEqualTo(usuario.getSenhaProvisoriaExpiraEm());
        assertThat(evento.toString()).doesNotContain(CPF, "escola.com");
    }

    @Test
    void emailJaUsadoComoLoginEhRecusado() {
        autenticarComo("SECRETARIA");
        when(usuarioRepository.existsByLogin("ana.souza@escola.com")).thenReturn(true);

        assertThatThrownBy(() -> acessoService.enviarAcessoInicial(pessoa(), PerfilUsuario.PROFESSOR))
                .isInstanceOf(LoginJaCadastradoException.class);
        verify(usuarioRepository, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void semEmailOuSemCpfNaoCriaAcesso() {
        autenticarComo("SECRETARIA");
        Pessoa semEmail = pessoa();
        semEmail.setEmail(" ");
        Pessoa semCpf = pessoa();
        semCpf.setCpf(null);

        assertThatThrownBy(() -> acessoService.enviarAcessoInicial(semEmail, PerfilUsuario.PROFESSOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> acessoService.enviarAcessoInicial(semCpf, PerfilUsuario.PROFESSOR))
                .isInstanceOf(IllegalArgumentException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void respeitaAHierarquiaDePerfis() {
        autenticarComo("FINANCEIRO");

        assertThatThrownBy(() -> acessoService.enviarAcessoInicial(pessoa(), PerfilUsuario.PROFESSOR))
                .isInstanceOf(PerfilNaoPermitidoException.class);

        autenticarComo("SECRETARIA");
        assertThatThrownBy(() -> acessoService.enviarAcessoInicial(pessoa(), PerfilUsuario.MASTER))
                .isInstanceOf(PerfilNaoPermitidoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void reenvioRenovaSenhaEValidadeDeQuemAindaNaoTrocou() {
        autenticarComo("SECRETARIA");
        Pessoa pessoa = pessoa();
        Usuario existente = Usuario.builder().id(UUID.randomUUID()).pessoaId(pessoa.getId())
                .login("ana.souza@escola.com").senhaHash("{bcrypt}antigo").perfil(PerfilUsuario.PROFESSOR)
                .ativo(true).senhaProvisoria(true).senhaProvisoriaExpiraEm(AGORA.minusSeconds(60)).build();
        when(usuarioRepository.findByPessoaId(pessoa.getId())).thenReturn(Optional.of(existente));
        stubSave();

        Usuario usuario = acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.PROFESSOR);

        assertThat(usuario.getId()).isEqualTo(existente.getId());
        assertThat(usuario.getSenhaProvisoriaExpiraEm()).isEqualTo(AGORA.plus(Duration.ofDays(7)));
        assertThat(passwordEncoder.matches(CPF, usuario.getSenhaHash())).isTrue();
        verify(usuarioRepository, never()).existsByLogin(any());
        verify(eventos).publishEvent(any(AcessoInicialCriado.class));
    }

    @Test
    void reenvioParaQuemJaDefiniuASenhaEhRecusado() {
        autenticarComo("SECRETARIA");
        Pessoa pessoa = pessoa();
        Usuario ativado = Usuario.builder().id(UUID.randomUUID()).pessoaId(pessoa.getId())
                .login("ana.souza@escola.com").senhaHash("{bcrypt}definitiva").ativo(true)
                .senhaProvisoria(false).build();
        when(usuarioRepository.findByPessoaId(pessoa.getId())).thenReturn(Optional.of(ativado));

        assertThatThrownBy(() -> acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.PROFESSOR))
                .isInstanceOf(AcessoJaAtivadoException.class);
        assertThat(ativado.getSenhaHash()).isEqualTo("{bcrypt}definitiva");
        verify(usuarioRepository, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void trocaDeEmailTrocaOLogin() {
        Pessoa pessoa = pessoa();
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).login("antigo@escola.com").build();
        when(usuarioRepository.findByPessoaId(pessoa.getId())).thenReturn(Optional.of(usuario));

        acessoService.sincronizarLogin(pessoa);

        assertThat(usuario.getLogin()).isEqualTo("ana.souza@escola.com");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void trocaParaEmailJaUsadoComoLoginEhRecusada() {
        Pessoa pessoa = pessoa();
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).login("antigo@escola.com").build();
        when(usuarioRepository.findByPessoaId(pessoa.getId())).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByLogin("ana.souza@escola.com")).thenReturn(true);

        assertThatThrownBy(() -> acessoService.sincronizarLogin(pessoa))
                .isInstanceOf(LoginJaCadastradoException.class);
        assertThat(usuario.getLogin()).isEqualTo("antigo@escola.com");
    }

    @Test
    void mesmoEmailNaoRegravaOLogin() {
        Pessoa pessoa = pessoa();
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).login("ana.souza@escola.com").build();
        when(usuarioRepository.findByPessoaId(pessoa.getId())).thenReturn(Optional.of(usuario));

        acessoService.sincronizarLogin(pessoa);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void inativarPessoaInativaOLogin() {
        Pessoa pessoa = pessoa();
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).ativo(true).build();
        when(usuarioRepository.findByPessoaId(pessoa.getId())).thenReturn(Optional.of(usuario));

        acessoService.definirAtivo(pessoa, false);

        assertThat(usuario.isAtivo()).isFalse();
        verify(usuarioRepository).save(usuario);
    }
}
