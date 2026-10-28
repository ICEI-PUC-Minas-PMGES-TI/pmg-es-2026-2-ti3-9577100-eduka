package br.pucminas.eduka.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.exception.CredenciaisInvalidasException;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
class DemoAcessoServiceTest {

    private static final String LOGIN = "edukasystem@gmail.com";
    private static final String SENHA = "senha-do-acesso";
    private static final String MASTER = "helena.duarte@mock.eduka.com.br";
    private static final String SUPER_ADMIN = "admin@eduka.com.br";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtService jwtService;

    private DemoAcessoService service;

    @BeforeEach
    void setUp() {
        service = new DemoAcessoService(new DemoAcessoProperties(LOGIN, SENHA), usuarioRepository, jwtService,
                "Admin@Eduka.com.br");
        lenient().when(usuarioRepository.findByLogin(anyString())).thenReturn(Optional.empty());
    }

    private Usuario usuario(String login, PerfilUsuario perfil, boolean ativo) {
        Usuario usuario = Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(perfil == PerfilUsuario.SUPER_ADMIN ? null : UUID.randomUUID())
                .login(login)
                .perfil(perfil)
                .ativo(ativo)
                .build();
        when(usuarioRepository.findByLogin(login)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    @Test
    void listaSoAsContasQueExistemEEstaoAtivas() {
        usuario(MASTER, PerfilUsuario.MASTER, true);
        usuario("laura.fernandes@mock.eduka.com.br", PerfilUsuario.ALUNO, false);
        usuario(SUPER_ADMIN, PerfilUsuario.SUPER_ADMIN, true);

        var grupos = service.contas(" EdukaSystem@gmail.com ", SENHA);

        assertThat(grupos).hasSize(2);
        assertThat(grupos.get(0).contas()).extracting(DemoAcessoService.Conta::login).containsExactly(MASTER);
        assertThat(grupos.get(1).contas()).extracting(DemoAcessoService.Conta::login).containsExactly(SUPER_ADMIN);
    }

    @Test
    void acessoErradoNaoListaNada() {
        assertThatThrownBy(() -> service.contas(LOGIN, "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> service.contas("outro@gmail.com", SENHA))
                .isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> service.contas(null, null)).isInstanceOf(CredenciaisInvalidasException.class);
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void entraNaContaDaListaSemASenhaDela() {
        Usuario master = usuario(MASTER, PerfilUsuario.MASTER, true);
        when(jwtService.generateAccessToken(master.getId(), "MASTER", master.getEscolaId())).thenReturn("access");
        when(jwtService.generateRefreshToken(master.getId())).thenReturn("refresh");

        LoginResponse resposta = service.entrar(LOGIN, SENHA, MASTER);

        assertThat(resposta.accessToken()).isEqualTo("access");
        assertThat(resposta.perfil()).isEqualTo(PerfilUsuario.MASTER);
        assertThat(resposta.verificacao()).isNull();
    }

    @Test
    void naoEntraEmContaForaDaLista() {
        assertThatThrownBy(() -> service.entrar(LOGIN, SENHA, "diretor@escola-real.com.br"))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verifyNoInteractions(jwtService);
    }

    @Test
    void naoEntraComAcessoErradoNemEmContaInativa() {
        usuario(MASTER, PerfilUsuario.MASTER, false);

        assertThatThrownBy(() -> service.entrar(LOGIN, SENHA, MASTER))
                .isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> service.entrar(LOGIN, "errada", MASTER))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verifyNoInteractions(jwtService);
    }

    @Test
    void semSuperAdminInicialMostraSoAEscola() {
        service = new DemoAcessoService(new DemoAcessoProperties(LOGIN, SENHA), usuarioRepository, jwtService, "");
        usuario(MASTER, PerfilUsuario.MASTER, true);

        assertThat(service.contas(LOGIN, SENHA)).extracting(DemoAcessoService.Grupo::titulo)
                .containsExactly("Colégio Ipê Amarelo");
    }
}
