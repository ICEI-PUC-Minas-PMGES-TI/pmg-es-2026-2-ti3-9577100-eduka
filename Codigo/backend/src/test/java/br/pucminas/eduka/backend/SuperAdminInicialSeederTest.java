package br.pucminas.eduka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.exception.acesso.SenhaInvalidaException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;

@ExtendWith(MockitoExtension.class)
class SuperAdminInicialSeederTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private PessoaPapelRepository pessoaPapelRepository;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private SuperAdminInicialSeeder seeder(String login, String senha) {
        return new SuperAdminInicialSeeder(usuarioRepository, pessoaRepository, pessoaPapelRepository,
                passwordEncoder, login, senha);
    }

    @Test
    void criaOSuperAdminComLoginNormalizado() {
        when(usuarioRepository.existsByLogin("admin@eduka.com.br")).thenReturn(false);
        when(pessoaRepository.save(any(Pessoa.class)))
                .thenAnswer(invocacao -> {
                    Pessoa pessoa = invocacao.getArgument(0);
                    pessoa.setId(UUID.randomUUID());
                    return pessoa;
                });

        seeder(" Admin@Eduka.com.br ", "senha-forte-123").run(null);

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuario.capture());
        assertThat(usuario.getValue().getLogin()).isEqualTo("admin@eduka.com.br");
        assertThat(usuario.getValue().getPerfil()).isEqualTo(PerfilUsuario.SUPER_ADMIN);
        assertThat(usuario.getValue().getEscolaId()).isNull();
        assertThat(passwordEncoder.matches("senha-forte-123", usuario.getValue().getSenhaHash())).isTrue();
    }

    @Test
    void semVariaveisNaoFazNada() {
        seeder("", "").run(null);
        seeder("admin@eduka.com.br", "").run(null);

        verifyNoInteractions(usuarioRepository, pessoaRepository, pessoaPapelRepository);
    }

    @Test
    void loginExistenteNaoETocado() {
        when(usuarioRepository.existsByLogin("admin@eduka.com.br")).thenReturn(true);

        seeder("admin@eduka.com.br", "senha-forte-123").run(null);

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(pessoaRepository);
    }

    @Test
    void senhaCurtaDerrubaASubida() {
        when(usuarioRepository.existsByLogin("admin@eduka.com.br")).thenReturn(false);

        assertThatThrownBy(() -> seeder("admin@eduka.com.br", "curta").run(null))
                .isInstanceOf(SenhaInvalidaException.class);
        verifyNoInteractions(pessoaRepository);
    }
}
