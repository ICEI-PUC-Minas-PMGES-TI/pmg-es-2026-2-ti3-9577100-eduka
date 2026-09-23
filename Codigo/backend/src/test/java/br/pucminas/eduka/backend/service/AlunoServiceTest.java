package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Isolamento por escola no servico de aluno: toda consulta e escrita usa a
 * escola do token, e nunca um valor vindo de fora.
 */
@ExtendWith(MockitoExtension.class)
class AlunoServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();

    @Mock
    private AlunoRepository alunoRepository;

    private AlunoService alunoService;

    @BeforeEach
    void setUp() {
        // IsolamentoEscola real: o ponto e provar que o servico consulta o
        // contexto de seguranca, nao um mock que devolve o que quisermos.
        alunoService = new AlunoService(alunoRepository, new IsolamentoEscola());
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

    private static Aluno aluno(UUID escolaId) {
        return Aluno.builder()
                .id(UUID.randomUUID())
                .escolaId(escolaId)
                .nome("Ana")
                .matricula("2026-001")
                .build();
    }

    @Test
    void listarFiltraPelaEscolaDoToken() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Aluno daEscolaA = aluno(ESCOLA_A);
        when(alunoRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(daEscolaA));

        List<Aluno> resultado = alunoService.listar();

        assertThat(resultado).containsExactly(daEscolaA);
        verify(alunoRepository, never()).findAll();
    }

    @Test
    void buscarPorIdConsultaSempreComEscolaDoToken() {
        autenticarComo("MASTER", ESCOLA_A);
        Aluno esperado = aluno(ESCOLA_A);
        when(alunoRepository.findByIdAndEscolaId(esperado.getId(), ESCOLA_A)).thenReturn(Optional.of(esperado));

        Aluno resultado = alunoService.buscarPorId(esperado.getId());

        assertThat(resultado).isSameAs(esperado);
        verify(alunoRepository, never()).findById(any());
    }

    @Test
    void alunoDeOutraEscolaEhTratadoComoInexistente() {
        autenticarComo("MASTER", ESCOLA_A);
        Aluno daEscolaB = aluno(ESCOLA_B);
        when(alunoRepository.findByIdAndEscolaId(daEscolaB.getId(), ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alunoService.buscarPorId(daEscolaB.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void cadastrarGravaEscolaDoTokenENaoDoCliente() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> inv.getArgument(0));

        alunoService.cadastrar(new AlunoRequest("Ana", "2026-001", null, null, null));

        ArgumentCaptor<Aluno> captor = ArgumentCaptor.forClass(Aluno.class);
        verify(alunoRepository).save(captor.capture());
        assertThat(captor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
    }

    @Test
    void cadastrarSemNomeOuMatriculaEhRejeitado() {
        autenticarComo("SECRETARIA", ESCOLA_A);

        assertThatThrownBy(() -> alunoService.cadastrar(new AlunoRequest(" ", "2026-001", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> alunoService.cadastrar(new AlunoRequest("Ana", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(alunoRepository, never()).save(any());
    }

    @Test
    void semUsuarioAutenticadoNadaEhConsultado() {
        assertThatThrownBy(() -> alunoService.listar())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> alunoService.buscarPorId(UUID.randomUUID()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verify(alunoRepository, never()).findAll();
        verify(alunoRepository, never()).findAllByEscolaId(any());
        verify(alunoRepository, never()).findById(any());
        verify(alunoRepository, never()).findByIdAndEscolaId(any(), any());
    }
}
