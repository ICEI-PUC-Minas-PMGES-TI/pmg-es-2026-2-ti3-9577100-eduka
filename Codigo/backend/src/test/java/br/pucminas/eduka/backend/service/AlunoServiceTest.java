// Codigo/backend/src/test/java/br/pucminas/eduka/backend/service/AlunoServiceTest.java
package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import br.pucminas.eduka.backend.domain.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.dto.EnderecoRequest;
import br.pucminas.eduka.backend.dto.ResponsavelRequest;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Isolamento por escola no servico de aluno: toda consulta e escrita usa a
 * escola do token, e nunca um valor vindo de fora. Cobre tambem a busca por
 * nome parcial (decifrada em memoria) e a montagem do AlunoResponse a partir
 * de Aluno + Pessoa.
 */
@ExtendWith(MockitoExtension.class)
class AlunoServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();

    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;
    @Mock
    private AlunoResponsavelRepository alunoResponsavelRepository;

    private AlunoService alunoService;

    @BeforeEach
    void setUp() {
        // IsolamentoEscola real: o ponto e provar que o servico consulta o
        // contexto de seguranca, nao um mock que devolve o que quisermos.
        alunoService = new AlunoService(alunoRepository, pessoaRepository, pessoaPapelRepository,
                alunoResponsavelRepository, new IsolamentoEscola());
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

    private static Aluno aluno(UUID escolaId, UUID pessoaId) {
        return Aluno.builder().id(UUID.randomUUID()).escolaId(escolaId).pessoaId(pessoaId)
                .matricula("2026-001").build();
    }

    private static Pessoa pessoa(String nome) {
        return Pessoa.builder().id(UUID.randomUUID()).nome(nome).cpf("12345678900").ativo(true).build();
    }

    @Test
    void listarFiltraPelaEscolaDoTokenENuncaVazaCpfOuDataNascimento() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana");
        Aluno daEscolaA = aluno(ESCOLA_A, pessoaAna.getId());
        when(alunoRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(daEscolaA));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));

        List<AlunoResponse> resultado = alunoService.listar(null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nome()).isEqualTo("Ana");
        assertThat(resultado.get(0).cpf()).isNull();
        assertThat(resultado.get(0).dataNascimento()).isNull();
        verify(alunoRepository, never()).findAll();
        verify(pessoaRepository, never()).findAllById(any());
    }

    @Test
    void listarFiltraPorNomeParcialDecifradoIgnorandoCaixaEEspacos() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana Silva");
        Pessoa pessoaCarlos = pessoa("Carlos Souza");
        Aluno alunoAna = aluno(ESCOLA_A, pessoaAna.getId());
        Aluno alunoCarlos = aluno(ESCOLA_A, pessoaCarlos.getId());
        when(alunoRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(alunoAna, alunoCarlos));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna, pessoaCarlos));

        List<AlunoResponse> resultado = alunoService.listar(" AN ");

        assertThat(resultado).extracting(AlunoResponse::nome).containsExactly("Ana Silva");
    }

    @Test
    void buscarPorIdConsultaSempreComEscolaDoTokenEMontaEnderecoParcial() {
        autenticarComo("MASTER", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana");
        pessoaAna.setCidade("Belo Horizonte");
        Aluno esperado = aluno(ESCOLA_A, pessoaAna.getId());
        when(alunoRepository.findByIdAndEscolaId(esperado.getId(), ESCOLA_A)).thenReturn(Optional.of(esperado));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(alunoResponsavelRepository.findAllById_AlunoId(esperado.getId())).thenReturn(List.of());

        AlunoResponse resultado = alunoService.buscarPorId(esperado.getId());

        assertThat(resultado.id()).isEqualTo(esperado.getId());
        assertThat(resultado.nome()).isEqualTo("Ana");
        assertThat(resultado.cpf()).isEqualTo("12345678900");
        assertThat(resultado.endereco().cidade()).isEqualTo("Belo Horizonte");
        assertThat(resultado.endereco().logradouro()).isNull();
        verify(alunoRepository, never()).findById(any());
        verify(pessoaRepository, never()).findById(any());
    }

    @Test
    void alunoDeOutraEscolaEhTratadoComoInexistente() {
        autenticarComo("MASTER", ESCOLA_A);
        Aluno daEscolaB = aluno(ESCOLA_B, UUID.randomUUID());
        when(alunoRepository.findByIdAndEscolaId(daEscolaB.getId(), ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alunoService.buscarPorId(daEscolaB.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void buscarPorIdNaoVazaPessoaDeOutraEscolaMesmoComAlunoDaEscolaCerta() {
        // Defesa em profundidade: mesmo que o aluno bata com a escola do token, a
        // busca da Pessoa tambem tem que ser filtrada por escola - nunca so por id.
        autenticarComo("MASTER", ESCOLA_A);
        Aluno esperado = aluno(ESCOLA_A, UUID.randomUUID());
        when(alunoRepository.findByIdAndEscolaId(esperado.getId(), ESCOLA_A)).thenReturn(Optional.of(esperado));
        when(pessoaRepository.findByIdAndEscolaId(esperado.getPessoaId(), ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alunoService.buscarPorId(esperado.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void cadastrarGravaEscolaDoTokenENaoDoClienteESemResponsaveis() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> inv.getArgument(0));

        AlunoResponse resultado = alunoService
                .cadastrar(new AlunoRequest("Ana", null, null, null, "2026-001", null, null));

        ArgumentCaptor<Pessoa> pessoaCaptor = ArgumentCaptor.forClass(Pessoa.class);
        verify(pessoaRepository).save(pessoaCaptor.capture());
        assertThat(pessoaCaptor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);

        ArgumentCaptor<Aluno> alunoCaptor = ArgumentCaptor.forClass(Aluno.class);
        verify(alunoRepository).save(alunoCaptor.capture());
        assertThat(alunoCaptor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);

        assertThat(resultado.responsaveis()).isEmpty();
        verify(alunoResponsavelRepository, never()).save(any());
    }

    @Test
    void cadastrarComResponsavelCriaPessoaEVinculo() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> {
            Aluno aluno = inv.getArgument(0);
            aluno.setId(UUID.randomUUID());
            return aluno;
        });

        AlunoResponse resultado = alunoService.cadastrar(new AlunoRequest("Ana", null, null, null, "2026-001", null,
                List.of(new ResponsavelRequest("Maria Silva", null, "maria@example.com", "31999998888", "MAE"))));

        assertThat(resultado.responsaveis()).hasSize(1);
        assertThat(resultado.responsaveis().get(0).nome()).isEqualTo("Maria Silva");
        assertThat(resultado.responsaveis().get(0).email()).isEqualTo("maria@example.com");
        assertThat(resultado.responsaveis().get(0).telefone()).isEqualTo("31999998888");
        assertThat(resultado.responsaveis().get(0).parentesco()).isEqualTo("MAE");

        ArgumentCaptor<Pessoa> pessoaCaptor = ArgumentCaptor.forClass(Pessoa.class);
        verify(pessoaRepository, times(2)).save(pessoaCaptor.capture());
        UUID pessoaAlunoId = pessoaCaptor.getAllValues().get(0).getId();
        UUID pessoaResponsavelId = pessoaCaptor.getAllValues().get(1).getId();

        ArgumentCaptor<Aluno> alunoCaptor = ArgumentCaptor.forClass(Aluno.class);
        verify(alunoRepository).save(alunoCaptor.capture());
        UUID alunoId = alunoCaptor.getValue().getId();

        ArgumentCaptor<PessoaPapel> papelCaptor = ArgumentCaptor.forClass(PessoaPapel.class);
        verify(pessoaPapelRepository, times(2)).save(papelCaptor.capture());
        assertThat(papelCaptor.getAllValues()).extracting(PessoaPapel::getPapel)
                .containsExactlyInAnyOrder(PapelPessoa.ALUNO, PapelPessoa.RESPONSAVEL);

        ArgumentCaptor<AlunoResponsavel> vinculoCaptor = ArgumentCaptor.forClass(AlunoResponsavel.class);
        verify(alunoResponsavelRepository).save(vinculoCaptor.capture());
        AlunoResponsavel vinculo = vinculoCaptor.getValue();
        assertThat(vinculo.getId().alunoId()).isEqualTo(alunoId);
        assertThat(vinculo.getId().pessoaId()).isEqualTo(pessoaResponsavelId);
        assertThat(vinculo.getId().pessoaId()).isNotEqualTo(pessoaAlunoId);
        assertThat(vinculo.getParentesco()).isEqualTo("MAE");
    }

    @Test
    void cadastrarComEnderecoParcialNaoExigeTodosOsCampos() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> inv.getArgument(0));

        AlunoResponse resultado = alunoService.cadastrar(new AlunoRequest("Ana", null, null,
                new EnderecoRequest(null, null, null, "Belo Horizonte", null, null), "2026-001", null, null));

        assertThat(resultado.endereco().cidade()).isEqualTo("Belo Horizonte");
        assertThat(resultado.endereco().logradouro()).isNull();
    }

    @Test
    void cadastrarSemNomeOuMatriculaEhRejeitado() {
        autenticarComo("SECRETARIA", ESCOLA_A);

        assertThatThrownBy(
                () -> alunoService.cadastrar(new AlunoRequest(" ", null, null, null, "2026-001", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> alunoService.cadastrar(new AlunoRequest("Ana", null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(alunoRepository, never()).save(any());
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void semUsuarioAutenticadoNadaEhConsultado() {
        assertThatThrownBy(() -> alunoService.listar(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> alunoService.buscarPorId(UUID.randomUUID()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verify(alunoRepository, never()).findAll();
        verify(alunoRepository, never()).findAllByEscolaId(any());
        verify(alunoRepository, never()).findById(any());
        verify(alunoRepository, never()).findByIdAndEscolaId(any(), any());
    }
}
