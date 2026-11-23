package br.pucminas.eduka.backend.service.escola;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.exception.escola.LimiteUsuariosAtingidoException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;

@ExtendWith(MockitoExtension.class)
class LimiteUsuariosServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Mock
    private EscolaRepository escolaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private LimiteUsuariosService service;

    @BeforeEach
    void setUp() {
        service = new LimiteUsuariosService(escolaRepository, usuarioRepository);
    }

    @Test
    void escolaSemLimiteNaoConta() {
        when(escolaRepository.travarLimiteUsuarios(ESCOLA)).thenReturn(Optional.empty());

        assertThatCode(() -> service.exigirVaga(ESCOLA)).doesNotThrowAnyException();
        verify(usuarioRepository, never()).countByEscolaIdAndAtivoTrue(any());
    }

    @Test
    void abaixoDoLimiteTemVaga() {
        when(escolaRepository.travarLimiteUsuarios(ESCOLA)).thenReturn(Optional.of(3));
        when(usuarioRepository.countByEscolaIdAndAtivoTrue(ESCOLA)).thenReturn(2L);

        assertThatCode(() -> service.exigirVaga(ESCOLA)).doesNotThrowAnyException();
    }

    @Test
    void noLimiteNaoTemVaga() {
        when(escolaRepository.travarLimiteUsuarios(ESCOLA)).thenReturn(Optional.of(3));
        when(usuarioRepository.countByEscolaIdAndAtivoTrue(ESCOLA)).thenReturn(3L);

        assertThatThrownBy(() -> service.exigirVaga(ESCOLA))
                .isInstanceOf(LimiteUsuariosAtingidoException.class)
                .extracting("limite").isEqualTo(3);
    }

    @Test
    void limiteBaixadoAbaixoDoUsoTambemBarra() {
        when(escolaRepository.travarLimiteUsuarios(ESCOLA)).thenReturn(Optional.of(3));
        when(usuarioRepository.countByEscolaIdAndAtivoTrue(ESCOLA)).thenReturn(10L);

        assertThatThrownBy(() -> service.exigirVaga(ESCOLA)).isInstanceOf(LimiteUsuariosAtingidoException.class);
    }

    @Test
    void semEscolaNaoHaLimite() {
        assertThatCode(() -> service.exigirVaga(null)).doesNotThrowAnyException();
        verifyNoInteractions(escolaRepository, usuarioRepository);
    }
}
