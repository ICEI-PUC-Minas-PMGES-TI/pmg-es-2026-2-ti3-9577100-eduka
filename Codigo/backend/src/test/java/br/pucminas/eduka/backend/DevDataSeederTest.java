package br.pucminas.eduka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class DevDataSeederTest {

    private static final String LOGIN_SUPER_ADMIN = "admin@eduka.com.br";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;
    @Mock
    private EscolaRepository escolaRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private DevDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DevDataSeeder(usuarioRepository, pessoaRepository, pessoaPapelRepository, escolaRepository,
                passwordEncoder);
    }

    @Test
    void criaSuperAdminQuandoAindaNaoExiste() {
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(true);
        when(usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)).thenReturn(false);
        salvarPessoaComId();
        when(passwordEncoder.encode("Eduka-admin123")).thenReturn("{bcrypt}hash");

        seeder.run(null);

        verify(pessoaRepository).save(any(Pessoa.class));
        verify(pessoaPapelRepository).save(any());
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getLogin()).isEqualTo(LOGIN_SUPER_ADMIN);
        assertThat(usuarioCaptor.getValue().getSenhaHash()).isEqualTo("{bcrypt}hash");
        assertThat(usuarioCaptor.getValue().getEscolaId()).isNull();
    }

    @Test
    void naoRecriaQuandoTodosJaExistem() {
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(true);

        seeder.run(null);

        verify(pessoaRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void criaEscolaDevEUmUsuarioPorPerfilDeEscola() {
        UUID escolaDevId = UUID.randomUUID();
        Escola escolaDev = mock(Escola.class);
        when(escolaDev.getId()).thenReturn(escolaDevId);
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(false);
        when(usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)).thenReturn(true);
        when(usuarioRepository.findByLogin(anyString())).thenReturn(Optional.empty());
        when(escolaRepository.save(any(Escola.class))).thenReturn(escolaDev);
        salvarPessoaComId();
        when(passwordEncoder.encode("Eduka-dev123")).thenReturn("{bcrypt}dev");

        seeder.run(null);

        verify(escolaRepository).save(any(Escola.class));
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(5)).save(usuarioCaptor.capture());
        List<Usuario> criados = usuarioCaptor.getAllValues();
        assertThat(criados).extracting(Usuario::getPerfil).containsExactly(PerfilUsuario.MASTER,
                PerfilUsuario.SECRETARIA, PerfilUsuario.FINANCEIRO, PerfilUsuario.PROFESSOR, PerfilUsuario.ALUNO);
        assertThat(criados).extracting(Usuario::getEscolaId).containsOnly(escolaDevId);
        assertThat(criados).extracting(Usuario::getSenhaHash).containsOnly("{bcrypt}dev");
    }

    @Test
    void reaproveitaEscolaDevDeUsuarioJaCriado() {
        UUID escolaDevId = UUID.randomUUID();
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(false);
        when(usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)).thenReturn(true);
        when(usuarioRepository.existsByLogin("master@dev.eduka.com.br")).thenReturn(true);
        when(usuarioRepository.findByLogin("master@dev.eduka.com.br"))
                .thenReturn(Optional.of(Usuario.builder().escolaId(escolaDevId).build()));
        salvarPessoaComId();

        seeder.run(null);

        verify(escolaRepository, never()).save(any());
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(4)).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getAllValues()).extracting(Usuario::getEscolaId).containsOnly(escolaDevId);
    }

    private void salvarPessoaComId() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
    }
}
