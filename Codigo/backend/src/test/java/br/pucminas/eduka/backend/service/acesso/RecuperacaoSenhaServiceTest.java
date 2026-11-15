package br.pucminas.eduka.backend.service.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.RecuperacaoSenha;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.acesso.RecuperacaoSenhaResponse;
import br.pucminas.eduka.backend.exception.acesso.CodigoRecuperacaoInvalidoException;
import br.pucminas.eduka.backend.exception.acesso.SenhaInvalidaException;
import br.pucminas.eduka.backend.repository.acesso.RecuperacaoSenhaRepository;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.acesso.VerificacaoLoginRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-13T12:00:00Z");
    private static final String LOGIN = "ana@escola.com";
    private static final String NOVA_SENHA = "senha-nova-123";

    /** 15 min de validade, 3 tentativas, 60 s entre envios, 2 envios, janela de 60 min. */
    private static final RecuperacaoSenhaProperties REGRAS = new RecuperacaoSenhaProperties(15, 3, 60, 2, 60, false);

    @Mock
    private RecuperacaoSenhaRepository recuperacaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private VerificacaoLoginRepository verificacaoLoginRepository;

    @Mock
    private ApplicationEventPublisher eventos;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();
    private final RelogioAjustavel relogio = new RelogioAjustavel(AGORA);

    private RecuperacaoSenhaService service;
    private Usuario usuario;
    private Pessoa pessoa;

    /** O que o repositorio guardaria: a linha gravada por ultimo. */
    private RecuperacaoSenha gravada;

    @BeforeEach
    void setUp() {
        service = new RecuperacaoSenhaService(recuperacaoRepository, usuarioRepository, pessoaRepository,
                verificacaoLoginRepository, passwordEncoder, eventos, relogio, REGRAS);
        UUID escolaId = UUID.randomUUID();
        pessoa = Pessoa.builder().id(UUID.randomUUID()).escolaId(escolaId).nome("Ana").email("ana@pessoal.com")
                .build();
        usuario = Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .login(LOGIN)
                .senhaHash(passwordEncoder.encode("senha-antiga"))
                .perfil(PerfilUsuario.SECRETARIA)
                .ativo(true)
                .build();

        lenient().when(usuarioRepository.findByLogin(LOGIN)).thenReturn(Optional.of(usuario));
        lenient().when(pessoaRepository.findById(pessoa.getId())).thenReturn(Optional.of(pessoa));
        lenient().when(recuperacaoRepository.findParaAtualizarByUsuarioId(usuario.getId()))
                .thenAnswer(chamada -> Optional.ofNullable(gravada));
        lenient().when(recuperacaoRepository.save(any())).thenAnswer(chamada -> {
            gravada = chamada.getArgument(0);
            return gravada;
        });
    }

    private String ultimoCodigo() {
        ArgumentCaptor<CodigoRecuperacaoSenhaGerado> captor = ArgumentCaptor.forClass(CodigoRecuperacaoSenhaGerado.class);
        verify(eventos, atLeastOnce()).publishEvent(captor.capture());
        return captor.getValue().codigo();
    }

    private static String outroCodigo(String codigo) {
        return codigo.equals("000000") ? "111111" : "000000";
    }

    @Test
    void solicitarEnviaCodigoParaOEmailDoCadastro() {
        RecuperacaoSenhaResponse resposta = service.solicitar("  ANA@escola.com ");

        ArgumentCaptor<CodigoRecuperacaoSenhaGerado> captor = ArgumentCaptor.forClass(CodigoRecuperacaoSenhaGerado.class);
        verify(eventos).publishEvent(captor.capture());
        CodigoRecuperacaoSenhaGerado gerado = captor.getValue();
        assertThat(gerado.email()).isEqualTo("ana@pessoal.com");
        assertThat(gerado.nome()).isEqualTo("Ana");
        assertThat(gerado.codigo()).matches("\\d{6}");
        assertThat(gerado.toString()).doesNotContain(gerado.codigo());

        assertThat(gravada.getCodigoHash()).isNotEqualTo(gerado.codigo());
        assertThat(passwordEncoder.matches(gerado.codigo(), gravada.getCodigoHash())).isTrue();
        assertThat(gravada.getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));
        assertThat(gravada.getEnvios()).isEqualTo(1);
        assertThat(gravada.getJanelaInicio()).isEqualTo(AGORA);
        assertThat(resposta).isEqualTo(new RecuperacaoSenhaResponse(15 * 60, 60));
    }

    @Test
    void loginInexistenteRespondeIgualSemEnviarNada() {
        when(usuarioRepository.findByLogin("ninguem@escola.com")).thenReturn(Optional.empty());

        RecuperacaoSenhaResponse resposta = service.solicitar("ninguem@escola.com");

        assertThat(resposta).isEqualTo(new RecuperacaoSenhaResponse(15 * 60, 60));
        verifyNoInteractions(eventos, recuperacaoRepository);
    }

    @Test
    void usuarioInativoNaoRecebeCodigo() {
        usuario.setAtivo(false);

        service.solicitar(LOGIN);

        verifyNoInteractions(eventos, recuperacaoRepository);
    }

    @Test
    void usuarioSemEmailNaoRecebeCodigo() {
        usuario.setLogin("12345678900");
        pessoa.setEmail(null);
        when(usuarioRepository.findByLogin("12345678900")).thenReturn(Optional.of(usuario));

        service.solicitar("12345678900");

        verifyNoInteractions(eventos, recuperacaoRepository);
    }

    @Test
    void novoPedidoAntesDoIntervaloNaoEnviaOutroCodigo() {
        service.solicitar(LOGIN);
        relogio.avancar(Duration.ofSeconds(30));

        service.solicitar(LOGIN);

        verify(eventos, times(1)).publishEvent(any(CodigoRecuperacaoSenhaGerado.class));
    }

    @Test
    void novoPedidoDepoisDoIntervaloTrocaOCodigoSemDevolverTentativas() {
        service.solicitar(LOGIN);
        String primeiro = ultimoCodigo();
        assertThatThrownBy(() -> service.redefinir(LOGIN, outroCodigo(primeiro), NOVA_SENHA))
                .isInstanceOf(CodigoRecuperacaoInvalidoException.class);
        relogio.avancar(Duration.ofSeconds(61));

        service.solicitar(LOGIN);

        String segundo = ultimoCodigo();
        assertThat(passwordEncoder.matches(segundo, gravada.getCodigoHash())).isTrue();
        assertThat(gravada.getEnvios()).isEqualTo(2);
        assertThat(gravada.getTentativas()).isEqualTo(1);
    }

    @Test
    void limiteDeEnviosValeAteAJanelaPassar() {
        service.solicitar(LOGIN);
        relogio.avancar(Duration.ofSeconds(61));
        service.solicitar(LOGIN);
        relogio.avancar(Duration.ofSeconds(61));

        service.solicitar(LOGIN);
        verify(eventos, times(2)).publishEvent(any(CodigoRecuperacaoSenhaGerado.class));

        relogio.avancar(Duration.ofMinutes(60));
        service.solicitar(LOGIN);
        verify(eventos, times(3)).publishEvent(any(CodigoRecuperacaoSenhaGerado.class));
        assertThat(gravada.getEnvios()).isEqualTo(1);
        assertThat(gravada.getTentativas()).isZero();
    }

    @Test
    void redefinirComCodigoCertoTrocaASenhaEEncerraOPedido() {
        usuario.setSenhaProvisoria(true);
        usuario.setSenhaProvisoriaExpiraEm(AGORA.minusSeconds(1));
        service.solicitar(LOGIN);

        service.redefinir(LOGIN, ultimoCodigo(), NOVA_SENHA);

        assertThat(passwordEncoder.matches(NOVA_SENHA, usuario.getSenhaHash())).isTrue();
        assertThat(usuario.isSenhaProvisoria()).isFalse();
        assertThat(usuario.getSenhaProvisoriaExpiraEm()).isNull();
        // Quem pediu a recuperacao derruba as sessoes de quem ainda tem a senha antiga.
        assertThat(usuario.getSenhaAlteradaEm()).isEqualTo(AGORA);
        verify(usuarioRepository).save(usuario);
        verify(recuperacaoRepository).delete(gravada);
        verify(verificacaoLoginRepository).deleteByUsuarioId(usuario.getId());
    }

    @Test
    void codigoErradoContaTentativaENaoTrocaASenha() {
        service.solicitar(LOGIN);
        String hashAntes = usuario.getSenhaHash();

        assertThatThrownBy(() -> service.redefinir(LOGIN, outroCodigo(ultimoCodigo()), NOVA_SENHA))
                .isInstanceOf(CodigoRecuperacaoInvalidoException.class);

        assertThat(gravada.getTentativas()).isEqualTo(1);
        assertThat(usuario.getSenhaHash()).isEqualTo(hashAntes);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void tentativasEsgotadasQueimamOCodigoCerto() {
        service.solicitar(LOGIN);
        String codigo = ultimoCodigo();
        for (int i = 0; i < REGRAS.maxTentativas(); i++) {
            assertThatThrownBy(() -> service.redefinir(LOGIN, outroCodigo(codigo), NOVA_SENHA))
                    .isInstanceOf(CodigoRecuperacaoInvalidoException.class);
        }

        assertThatThrownBy(() -> service.redefinir(LOGIN, codigo, NOVA_SENHA))
                .isInstanceOf(CodigoRecuperacaoInvalidoException.class);

        // E nao adianta pedir outro codigo antes de a janela passar.
        relogio.avancar(Duration.ofMinutes(5));
        service.solicitar(LOGIN);
        verify(eventos, times(1)).publishEvent(any(CodigoRecuperacaoSenhaGerado.class));
    }

    @Test
    void codigoVencidoNaoServe() {
        service.solicitar(LOGIN);
        String codigo = ultimoCodigo();
        relogio.avancar(Duration.ofMinutes(15));

        assertThatThrownBy(() -> service.redefinir(LOGIN, codigo, NOVA_SENHA))
                .isInstanceOf(CodigoRecuperacaoInvalidoException.class);
        assertThat(gravada.getTentativas()).isZero();
    }

    @Test
    void semPedidoAbertoOuLoginInexistenteDaOMesmoErro() {
        when(usuarioRepository.findByLogin("ninguem@escola.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinir("ninguem@escola.com", "123456", NOVA_SENHA))
                .isInstanceOf(CodigoRecuperacaoInvalidoException.class);
        assertThatThrownBy(() -> service.redefinir(LOGIN, "123456", NOVA_SENHA))
                .isInstanceOf(CodigoRecuperacaoInvalidoException.class);
    }

    @Test
    void senhaForaDaRegraNaoGastaTentativa() {
        service.solicitar(LOGIN);

        assertThatThrownBy(() -> service.redefinir(LOGIN, outroCodigo(ultimoCodigo()), "curta"))
                .isInstanceOf(SenhaInvalidaException.class);

        assertThat(gravada.getTentativas()).isZero();
    }

    private static final class RelogioAjustavel extends Clock {

        private Instant agora;

        RelogioAjustavel(Instant agora) {
            this.agora = agora;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
