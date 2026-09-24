package br.pucminas.eduka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class DevDataSeederTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private DevDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DevDataSeeder(usuarioRepository, pessoaRepository, pessoaPapelRepository, passwordEncoder);
    }

    @Test
    void criaSuperAdminQuandoAindaNaoExiste() {
        when(usuarioRepository.existsByLogin("admin@eduka.com.br")).thenReturn(false);
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(passwordEncoder.encode("Eduka-admin123")).thenReturn("{bcrypt}hash");

        seeder.run(null);

        verify(pessoaRepository).save(any(Pessoa.class));
        verify(pessoaPapelRepository).save(any());
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getLogin()).isEqualTo("admin@eduka.com.br");
        assertThat(usuarioCaptor.getValue().getSenhaHash()).isEqualTo("{bcrypt}hash");
    }

    @Test
    void naoRecriaQuandoSuperAdminJaExiste() {
        when(usuarioRepository.existsByLogin("admin@eduka.com.br")).thenReturn(true);

        seeder.run(null);

        verify(pessoaRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }
}
