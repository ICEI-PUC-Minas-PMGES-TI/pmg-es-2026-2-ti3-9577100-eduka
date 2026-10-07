package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.domain.VerificacaoLogin;
import br.pucminas.eduka.backend.dto.VerificacaoLoginResponse;
import br.pucminas.eduka.backend.exception.CodigoVerificacaoExpiradoException;
import br.pucminas.eduka.backend.exception.CodigoVerificacaoIncorretoException;
import br.pucminas.eduka.backend.exception.ReenvioCodigoIndisponivelException;
import br.pucminas.eduka.backend.exception.VerificacaoLoginEncerradaException;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.repository.VerificacaoLoginRepository;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;

@ExtendWith(MockitoExtension.class)
class VerificacaoLoginServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-06T12:00:00Z");

    /** 5 min de validade, 3 tentativas, 60 s entre envios, 2 envios. */
    private static final VerificacaoLoginProperties REGRAS = new VerificacaoLoginProperties(true, 5, 3, 60, 2, false);

    @Mock
    private VerificacaoLoginRepository verificacaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private ApplicationEventPublisher eventos;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();
    private final RelogioAjustavel relogio = new RelogioAjustavel(AGORA);

    private VerificacaoLoginService service;
    private Usuario usuario;
    private Pessoa pessoa;

    @BeforeEach
    void setUp() {
        service = servico(REGRAS);
        UUID escolaId = UUID.randomUUID();
        pessoa = Pessoa.builder().id(UUID.randomUUID()).escolaId(escolaId).nome("Ana").email("ana@escola.com")
                .build();
        usuario = Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .login("ana@escola.com")
                .perfil(PerfilUsuario.PROFESSOR)
                .ativo(true)
                .build();
    }

    private VerificacaoLoginService servico(VerificacaoLoginProperties regras) {
        return new VerificacaoLoginService(verificacaoRepository, usuarioRepository, pessoaRepository,
                passwordEncoder, eventos, relogio, regras);
    }

    /** Abre uma verificacao e devolve a entidade gravada, ja com id e travavel pelo repositorio. */
    private VerificacaoLogin abrir() {
        when(pessoaRepository.findById(pessoa.getId())).thenReturn(Optional.of(pessoa));
        when(verificacaoRepository.save(any())).thenAnswer(chamada -> {
            VerificacaoLogin verificacao = chamada.getArgument(0);
            if (verificacao.getId() == null) {
                verificacao.setId(UUID.randomUUID());
            }
            return verificacao;
        });

        UUID id = service.iniciar(usuario).orElseThrow().id();

        ArgumentCaptor<VerificacaoLogin> captor = ArgumentCaptor.forClass(VerificacaoLogin.class);
        verify(verificacaoRepository, atLeastOnce()).save(captor.capture());
        VerificacaoLogin verificacao = captor.getValue();
        assertThat(verificacao.getId()).isEqualTo(id);
        // lenient: os testes que so abrem a verificacao nao chegam a busca-la.
        lenient().when(verificacaoRepository.findParaAtualizarById(id)).thenReturn(Optional.of(verificacao));
        return verificacao;
    }

    private String ultimoCodigo() {
        ArgumentCaptor<CodigoLoginGerado> captor = ArgumentCaptor.forClass(CodigoLoginGerado.class);
        verify(eventos, atLeastOnce()).publishEvent(captor.capture());
        return captor.getValue().codigo();
    }

    private static String outroCodigo(String codigo) {
        return codigo.equals("000000") ? "000001" : "000000";
    }

    @Test
    void iniciarGravaSoOHashEMandaCodigoDeSeisDigitosParaOEmailDaPessoa() {
        VerificacaoLogin verificacao = abrir();

        ArgumentCaptor<CodigoLoginGerado> captor = ArgumentCaptor.forClass(CodigoLoginGerado.class);
        verify(eventos).publishEvent(captor.capture());
        CodigoLoginGerado gerado = captor.getValue();
        assertThat(gerado.codigo()).matches("\\d{6}");
        assertThat(gerado.email()).isEqualTo("ana@escola.com");
        assertThat(gerado.nome()).isEqualTo("Ana");
        assertThat(gerado.toString()).doesNotContain(gerado.codigo());

        assertThat(verificacao.getCodigoHash()).doesNotContain(gerado.codigo());
        assertThat(passwordEncoder.matches(gerado.codigo(), verificacao.getCodigoHash())).isTrue();
        assertThat(verificacao.getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(5)));
    }

    @Test
    void iniciarDevolveOsPrazosEmSegundosEInvalidaAVerificacaoAnterior() {
        when(pessoaRepository.findById(pessoa.getId())).thenReturn(Optional.of(pessoa));
        when(verificacaoRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        VerificacaoLoginResponse resposta = service.iniciar(usuario).orElseThrow();

        assertThat(resposta.validadeSegundos()).isEqualTo(300);
        assertThat(resposta.reenvioSegundos()).isEqualTo(60);
        verify(verificacaoRepository).deleteByUsuarioId(usuario.getId());
    }

    @Test
    void iniciarComRecursoDesligadoNaoAbreVerificacao() {
        service = servico(new VerificacaoLoginProperties(false, 5, 3, 60, 2, false));

        assertThat(service.iniciar(usuario)).isEmpty();

        verifyNoInteractions(verificacaoRepository, eventos);
    }

    @Test
    void iniciarSemEmailNoCadastroUsaOLoginQuandoEleEhEmail() {
        pessoa.setEmail(null);
        abrir();

        ArgumentCaptor<CodigoLoginGerado> captor = ArgumentCaptor.forClass(CodigoLoginGerado.class);
        verify(eventos).publishEvent(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("ana@escola.com");
    }

    @Test
    void iniciarSemNenhumEmailNaoTrancaOUsuarioFora() {
        pessoa.setEmail(null);
        usuario.setLogin("20260001");
        when(pessoaRepository.findById(pessoa.getId())).thenReturn(Optional.of(pessoa));

        assertThat(service.iniciar(usuario)).isEmpty();

        verify(verificacaoRepository, never()).save(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void iniciarNaoUsaPessoaDeOutraEscola() {
        pessoa.setEscolaId(UUID.randomUUID());
        pessoa.setEmail("outra@escola.com");
        abrir();

        ArgumentCaptor<CodigoLoginGerado> captor = ArgumentCaptor.forClass(CodigoLoginGerado.class);
        verify(eventos).publishEvent(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("ana@escola.com");
    }

    @Test
    void confirmarComCodigoCertoDevolveOUsuarioEEncerraAVerificacao() {
        VerificacaoLogin verificacao = abrir();

        UUID usuarioId = service.confirmar(verificacao.getId(), ultimoCodigo());

        assertThat(usuarioId).isEqualTo(usuario.getId());
        verify(verificacaoRepository).delete(verificacao);
    }

    @Test
    void confirmarComCodigoErradoContaATentativaEMantemAVerificacao() {
        VerificacaoLogin verificacao = abrir();
        String errado = outroCodigo(ultimoCodigo());

        assertThatThrownBy(() -> service.confirmar(verificacao.getId(), errado))
                .isInstanceOf(CodigoVerificacaoIncorretoException.class);

        assertThat(verificacao.getTentativas()).isEqualTo(1);
        verify(verificacaoRepository, never()).delete(any());
    }

    @Test
    void confirmarEncerraAVerificacaoNaUltimaTentativaErrada() {
        VerificacaoLogin verificacao = abrir();
        String certo = ultimoCodigo();
        String errado = outroCodigo(certo);

        assertThatThrownBy(() -> service.confirmar(verificacao.getId(), errado))
                .isInstanceOf(CodigoVerificacaoIncorretoException.class);
        assertThatThrownBy(() -> service.confirmar(verificacao.getId(), errado))
                .isInstanceOf(CodigoVerificacaoIncorretoException.class);
        assertThatThrownBy(() -> service.confirmar(verificacao.getId(), errado))
                .isInstanceOf(VerificacaoLoginEncerradaException.class);

        verify(verificacaoRepository).delete(verificacao);
    }

    @Test
    void confirmarDepoisDaValidadeRecusaAteOCodigoCerto() {
        VerificacaoLogin verificacao = abrir();
        String certo = ultimoCodigo();
        relogio.avancar(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.confirmar(verificacao.getId(), certo))
                .isInstanceOf(CodigoVerificacaoExpiradoException.class);

        verify(verificacaoRepository, never()).delete(any());
    }

    @Test
    void confirmarUmSegundoAntesDeExpirarAindaVale() {
        VerificacaoLogin verificacao = abrir();
        String certo = ultimoCodigo();
        relogio.avancar(Duration.ofMinutes(5).minusSeconds(1));

        assertThat(service.confirmar(verificacao.getId(), certo)).isEqualTo(usuario.getId());
    }

    @Test
    void confirmarVerificacaoInexistenteEhEncerrada() {
        UUID id = UUID.randomUUID();
        when(verificacaoRepository.findParaAtualizarById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmar(id, "123456"))
                .isInstanceOf(VerificacaoLoginEncerradaException.class);
    }

    @Test
    void reenviarAntesDoIntervaloEhRecusado() {
        VerificacaoLogin verificacao = abrir();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        relogio.avancar(Duration.ofSeconds(59));

        assertThatThrownBy(() -> service.reenviar(verificacao.getId()))
                .isInstanceOf(ReenvioCodigoIndisponivelException.class);

        assertThat(verificacao.getEnvios()).isEqualTo(1);
    }

    @Test
    void reenviarTrocaOCodigoRenovaAValidadeEZeraAsTentativas() {
        VerificacaoLogin verificacao = abrir();
        String antigo = ultimoCodigo();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        assertThatThrownBy(() -> service.confirmar(verificacao.getId(), outroCodigo(antigo)))
                .isInstanceOf(CodigoVerificacaoIncorretoException.class);
        // Passa da validade: o reenvio vale tambem para codigo expirado.
        relogio.avancar(Duration.ofMinutes(6));

        VerificacaoLoginResponse resposta = service.reenviar(verificacao.getId());

        String novo = ultimoCodigo();
        assertThat(resposta.id()).isEqualTo(verificacao.getId());
        assertThat(resposta.validadeSegundos()).isEqualTo(300);
        assertThat(resposta.reenvioSegundos()).isEqualTo(60);
        assertThat(verificacao.getTentativas()).isZero();
        assertThat(verificacao.getEnvios()).isEqualTo(2);
        assertThat(passwordEncoder.matches(novo, verificacao.getCodigoHash())).isTrue();
        if (!novo.equals(antigo)) {
            assertThat(passwordEncoder.matches(antigo, verificacao.getCodigoHash())).isFalse();
        }
        assertThat(service.confirmar(verificacao.getId(), novo)).isEqualTo(usuario.getId());
    }

    @Test
    void reenviarAlemDoLimiteDeEnviosEhRecusado() {
        VerificacaoLogin verificacao = abrir();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        relogio.avancar(Duration.ofSeconds(60));
        service.reenviar(verificacao.getId());
        relogio.avancar(Duration.ofSeconds(60));

        assertThatThrownBy(() -> service.reenviar(verificacao.getId()))
                .isInstanceOf(ReenvioCodigoIndisponivelException.class);

        assertThat(verificacao.getEnvios()).isEqualTo(2);
    }

    @Test
    void reenviarParaUsuarioInativadoEhEncerrada() {
        VerificacaoLogin verificacao = abrir();
        usuario.setAtivo(false);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        relogio.avancar(Duration.ofSeconds(60));

        assertThatThrownBy(() -> service.reenviar(verificacao.getId()))
                .isInstanceOf(VerificacaoLoginEncerradaException.class);
    }

    /** Relogio que os testes adiantam para atravessar validade e intervalo de reenvio. */
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
