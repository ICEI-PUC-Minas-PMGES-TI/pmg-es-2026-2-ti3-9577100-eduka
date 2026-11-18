package br.pucminas.eduka.backend.service.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.escola.PlanoEscola;
import br.pucminas.eduka.backend.dto.acesso.LoginResponse;
import br.pucminas.eduka.backend.dto.acesso.VerificacaoLoginResponse;
import br.pucminas.eduka.backend.exception.acesso.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.exception.acesso.SenhaInvalidaException;
import br.pucminas.eduka.backend.exception.acesso.SenhaProvisoriaExpiradaException;
import br.pucminas.eduka.backend.exception.acesso.SessaoExpiradaException;
import br.pucminas.eduka.backend.exception.acesso.VerificacaoLoginEncerradaException;
import br.pucminas.eduka.backend.exception.escola.EscolaSuspensaException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;
import br.pucminas.eduka.backend.security.RefreshTokenClaims;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SENHA = "senha-forte-123";
    private static final String CPF = "52998224725";
    private static final Instant AGORA = Instant.parse("2026-09-28T12:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private VerificacaoLoginService verificacaoLoginService;

    @Mock
    private EscolaRepository escolaRepository;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        // O mock devolve Optional vazio em iniciar(): sem codigo de verificacao,
        // como com o recurso desligado. Os testes do codigo ligam explicitamente.
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService, verificacaoLoginService,
                escolaRepository, RELOGIO);
        usuario = Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(UUID.randomUUID())
                .pessoaId(UUID.randomUUID())
                .login("maria")
                .senhaHash(passwordEncoder.encode(SENHA))
                .perfil(PerfilUsuario.FINANCEIRO)
                .ativo(true)
                .build();
        // Por padrao a escola do usuario esta ativa; os testes de suspensao trocam.
        lenient().when(escolaRepository.findById(usuario.getEscolaId())).thenReturn(Optional.of(escola(true)));
    }

    private static Escola escola(boolean ativa) {
        return Escola.cadastrar("Escola", "11222333000181", null, "escola@eduka.com", null, PlanoEscola.values()[0],
                ativa);
    }

    @Test
    void loginDeEscolaSuspensaERecusadoDepoisDaSenha() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));
        when(escolaRepository.findById(usuario.getEscolaId())).thenReturn(Optional.of(escola(false)));

        assertThatThrownBy(() -> authService.login("maria", SENHA)).isInstanceOf(EscolaSuspensaException.class);
        verifyNoInteractions(jwtService);
    }

    @Test
    void senhaErradaDeEscolaSuspensaNaoRevelaASuspensao() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login("maria", "senha-errada"))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verifyNoInteractions(escolaRepository);
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
        // O acesso conta como atividade da escola no Painel de Escolas.
        verify(usuarioRepository).registrarAcesso(usuario.getId(), AGORA);
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

    private void comSenhaProvisoria(Instant expiraEm) {
        usuario.setSenhaHash(passwordEncoder.encode(CPF));
        usuario.setSenhaProvisoria(true);
        usuario.setSenhaProvisoriaExpiraEm(expiraEm);
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));
    }

    @Test
    void loginComSenhaProvisoriaNaoEmiteTokenEPedeTroca() {
        comSenhaProvisoria(AGORA.plusSeconds(3600));

        LoginResponse response = authService.login("maria", CPF);

        assertThat(response.trocaSenhaObrigatoria()).isTrue();
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(response.usuarioId()).isEqualTo(usuario.getId());
        verify(jwtService, never()).generateAccessToken(any(), any(), any());
        verify(jwtService, never()).generateRefreshToken(any());
    }

    @Test
    void loginComSenhaProvisoriaExpiradaEhRecusado() {
        comSenhaProvisoria(AGORA);

        assertThatThrownBy(() -> authService.login("maria", CPF))
                .isInstanceOf(SenhaProvisoriaExpiradaException.class);
    }

    @Test
    void loginComSenhaProvisoriaErradaNaoRevelaQueEhProvisoria() {
        comSenhaProvisoria(AGORA);

        assertThatThrownBy(() -> authService.login("maria", "11144477735"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void loginNormalDevolveTrocaSenhaObrigatoriaFalsa() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThat(authService.login("maria", SENHA).trocaSenhaObrigatoria()).isFalse();
    }

    @Test
    void definirSenhaDefinitivaTrocaAHashEEmiteTokens() {
        comSenhaProvisoria(AGORA.plusSeconds(3600));
        when(jwtService.generateAccessToken(usuario.getId(), "FINANCEIRO", usuario.getEscolaId()))
                .thenReturn("access-token");
        when(jwtService.generateRefreshToken(usuario.getId())).thenReturn("refresh-token");

        LoginResponse response = authService.definirSenhaDefinitiva(" MARIA ", CPF, "nova-senha-forte");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.trocaSenhaObrigatoria()).isFalse();
        assertThat(usuario.isSenhaProvisoria()).isFalse();
        assertThat(usuario.getSenhaProvisoriaExpiraEm()).isNull();
        assertThat(passwordEncoder.matches("nova-senha-forte", usuario.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches(CPF, usuario.getSenhaHash())).isFalse();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void loginComVerificacaoLigadaNaoEmiteTokenEDevolveAVerificacao() {
        VerificacaoLoginResponse verificacao = new VerificacaoLoginResponse(UUID.randomUUID(), 300, 60);
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));
        when(verificacaoLoginService.iniciar(usuario)).thenReturn(Optional.of(verificacao));

        LoginResponse response = authService.login("maria", SENHA);

        assertThat(response.verificacao()).isEqualTo(verificacao);
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(response.trocaSenhaObrigatoria()).isFalse();
        verify(jwtService, never()).generateAccessToken(any(), any(), any());
        // So conta como acesso quando o codigo e confirmado.
        verify(usuarioRepository, never()).registrarAcesso(any(), any());
    }

    @Test
    void loginComSenhaErradaNaoEnviaCodigo() {
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login("maria", "errada"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(verificacaoLoginService, never()).iniciar(any());
    }

    @Test
    void loginComSenhaProvisoriaNaoEnviaCodigoAntesDaTroca() {
        usuario.setSenhaProvisoria(true);
        usuario.setSenhaProvisoriaExpiraEm(AGORA.plusSeconds(60));
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        authService.login("maria", SENHA);

        verify(verificacaoLoginService, never()).iniciar(any());
        verify(usuarioRepository, never()).registrarAcesso(any(), any());
    }

    @Test
    void confirmarCodigoEmiteTokensDoUsuarioDaVerificacao() {
        UUID verificacaoId = UUID.randomUUID();
        when(verificacaoLoginService.confirmar(verificacaoId, "123456")).thenReturn(usuario.getId());
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(jwtService.generateAccessToken(usuario.getId(), "FINANCEIRO", usuario.getEscolaId()))
                .thenReturn("access-token");
        when(jwtService.generateRefreshToken(usuario.getId())).thenReturn("refresh-token");

        LoginResponse response = authService.confirmarCodigo(verificacaoId, "123456");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.verificacao()).isNull();
        verify(usuarioRepository).registrarAcesso(usuario.getId(), AGORA);
    }

    @Test
    void confirmarCodigoDeUsuarioInativadoNoMeioDoLoginNaoEmiteToken() {
        UUID verificacaoId = UUID.randomUUID();
        usuario.setAtivo(false);
        when(verificacaoLoginService.confirmar(verificacaoId, "123456")).thenReturn(usuario.getId());
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.confirmarCodigo(verificacaoId, "123456"))
                .isInstanceOf(VerificacaoLoginEncerradaException.class);

        verify(jwtService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void definirSenhaDefinitivaIgualAProvisoriaEhRecusada() {
        comSenhaProvisoria(AGORA.plusSeconds(3600));

        assertThatThrownBy(() -> authService.definirSenhaDefinitiva("maria", CPF, CPF))
                .isInstanceOf(SenhaInvalidaException.class);
        assertThat(usuario.isSenhaProvisoria()).isTrue();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void definirSenhaDefinitivaCurtaEhRecusada() {
        comSenhaProvisoria(AGORA.plusSeconds(3600));

        assertThatThrownBy(() -> authService.definirSenhaDefinitiva("maria", CPF, "curta"))
                .isInstanceOf(SenhaInvalidaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void definirSenhaDefinitivaComProvisoriaExpiradaEhRecusada() {
        comSenhaProvisoria(AGORA.minusSeconds(1));

        assertThatThrownBy(() -> authService.definirSenhaDefinitiva("maria", CPF, "nova-senha-forte"))
                .isInstanceOf(SenhaProvisoriaExpiradaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void definirSenhaDefinitivaNaoServeParaQuemJaTemSenhaDefinitiva() {
        // Sem isto a rota publica viraria uma troca de senha sem token.
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.definirSenhaDefinitiva("maria", SENHA, "nova-senha-forte"))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void definirSenhaDefinitivaComSenhaProvisoriaErradaEhRecusada() {
        comSenhaProvisoria(AGORA.plusSeconds(3600));

        assertThatThrownBy(() -> authService.definirSenhaDefinitiva("maria", "11144477735", "nova-senha-forte"))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void definirSenhaDefinitivaMarcaATrocaDeSenha() {
        comSenhaProvisoria(AGORA.plusSeconds(3600));

        authService.definirSenhaDefinitiva("maria", CPF, "nova-senha-forte");

        assertThat(usuario.getSenhaAlteradaEm()).isEqualTo(AGORA);
    }

    // ---- Renovacao da sessao (POST /login/renovacao) ----

    private void comRefreshValido(Instant emitidoEm) {
        when(jwtService.verifyRefreshToken("refresh-antigo"))
                .thenReturn(new RefreshTokenClaims(usuario.getId(), emitidoEm));
    }

    @Test
    void renovarSessaoDevolveNovoParDeTokens() {
        comRefreshValido(AGORA.minusSeconds(3600));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(jwtService.generateAccessToken(usuario.getId(), "FINANCEIRO", usuario.getEscolaId()))
                .thenReturn("access-novo");
        when(jwtService.generateRefreshToken(usuario.getId())).thenReturn("refresh-novo");

        LoginResponse response = authService.renovarSessao("refresh-antigo");

        assertThat(response.accessToken()).isEqualTo("access-novo");
        assertThat(response.refreshToken()).isEqualTo("refresh-novo");
        assertThat(response.perfil()).isEqualTo(PerfilUsuario.FINANCEIRO);
        verify(usuarioRepository).registrarAcesso(usuario.getId(), AGORA);
    }

    @Test
    void renovarSessaoUsaOPerfilAtualDoCadastro() {
        // O perfil mudou depois do login: o token novo sai com o perfil de agora.
        usuario.setPerfil(PerfilUsuario.SECRETARIA);
        comRefreshValido(AGORA.minusSeconds(60));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        authService.renovarSessao("refresh-antigo");

        verify(jwtService).generateAccessToken(usuario.getId(), "SECRETARIA", usuario.getEscolaId());
    }

    @Test
    void renovarSessaoComRefreshInvalidoEhRecusada() {
        when(jwtService.verifyRefreshToken("adulterado")).thenThrow(new JwtException("assinatura invalida"));

        assertThatThrownBy(() -> authService.renovarSessao("adulterado"))
                .isInstanceOf(SessaoExpiradaException.class);
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void renovarSessaoDeUsuarioInativoEhRecusada() {
        usuario.setAtivo(false);
        comRefreshValido(AGORA.minusSeconds(60));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.renovarSessao("refresh-antigo"))
                .isInstanceOf(SessaoExpiradaException.class);
        verify(jwtService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void renovarSessaoDeUsuarioInexistenteEhRecusada() {
        comRefreshValido(AGORA.minusSeconds(60));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.renovarSessao("refresh-antigo"))
                .isInstanceOf(SessaoExpiradaException.class);
    }

    @Test
    void renovarSessaoComSenhaVoltandoAProvisoriaEhRecusada() {
        usuario.setSenhaProvisoria(true);
        comRefreshValido(AGORA.minusSeconds(60));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.renovarSessao("refresh-antigo"))
                .isInstanceOf(SessaoExpiradaException.class);
    }

    @Test
    void renovarSessaoEmitidaAntesDaTrocaDeSenhaEhRecusada() {
        usuario.trocarSenha(usuario.getSenhaHash(), AGORA.minusSeconds(10));
        comRefreshValido(AGORA.minusSeconds(3600));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.renovarSessao("refresh-antigo"))
                .isInstanceOf(SessaoExpiradaException.class);
    }

    @Test
    void renovarSessaoEmitidaNoMesmoSegundoDaTrocaDeSenhaContinuaValendo() {
        // O iat do JWT so tem segundos: o login logo depois da troca nao pode cair.
        usuario.trocarSenha(usuario.getSenhaHash(), AGORA.plusMillis(700));
        comRefreshValido(AGORA);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        authService.renovarSessao("refresh-antigo");

        verify(jwtService).generateRefreshToken(usuario.getId());
    }

    @Test
    void renovarSessaoDeEscolaSuspensaRespondeComoNoLogin() {
        comRefreshValido(AGORA.minusSeconds(60));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(escolaRepository.findById(usuario.getEscolaId())).thenReturn(Optional.of(escola(false)));

        assertThatThrownBy(() -> authService.renovarSessao("refresh-antigo"))
                .isInstanceOf(EscolaSuspensaException.class);
        verify(jwtService, never()).generateRefreshToken(any());
    }
}
