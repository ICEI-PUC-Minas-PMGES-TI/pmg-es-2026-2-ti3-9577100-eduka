package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.MembroEquipeRequest;
import br.pucminas.eduka.backend.dto.MembroEquipeResponse;
import br.pucminas.eduka.backend.dto.MembroEquipeUpdateRequest;
import br.pucminas.eduka.backend.exception.CpfJaCadastradoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Equipe da escola (secretaria e financeiro): sempre na escola do token, so os
 * dois perfis, CPF unico na equipe (comparado em memoria, a coluna e cifrada),
 * login criado pelo {@link AcessoService} e inativacao no lugar da exclusao.
 */
@ExtendWith(MockitoExtension.class)
class EquipeServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final Set<PerfilUsuario> EQUIPE = Set.of(PerfilUsuario.SECRETARIA, PerfilUsuario.FINANCEIRO);

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;
    @Mock
    private AcessoService acessoService;

    private EquipeService equipeService;

    @BeforeEach
    void setUp() {
        equipeService = new EquipeService(usuarioRepository, pessoaRepository, pessoaPapelRepository, acessoService,
                new IsolamentoEscola());
        UsuarioAutenticado master = new UsuarioAutenticado(UUID.randomUUID(), "MASTER", ESCOLA_A);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(master, null, "ROLE_MASTER"));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Pessoa pessoa(String nome, String cpf) {
        return Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome(nome).cpf(cpf)
                .email(nome.toLowerCase() + "@escola.com").ativo(true).build();
    }

    private static Usuario usuario(Pessoa pessoa, PerfilUsuario perfil) {
        return Usuario.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoa.getId())
                .login(pessoa.getEmail()).senhaHash("hash").perfil(perfil).ativo(true).senhaProvisoria(true).build();
    }

    private static MembroEquipeRequest request(PerfilUsuario perfil, String cpf) {
        return new MembroEquipeRequest(perfil, " Ana Lima ", cpf, " ana@escola.com ", "31999998888");
    }

    private void semNinguemNaEquipe() {
        when(usuarioRepository.findAllByEscolaIdAndPerfilIn(eq(ESCOLA_A), anyCollection())).thenReturn(List.of());
    }

    private void comAEquipe(Pessoa... pessoas) {
        List<Usuario> usuarios = java.util.Arrays.stream(pessoas).map(p -> usuario(p, PerfilUsuario.SECRETARIA))
                .toList();
        when(usuarioRepository.findAllByEscolaIdAndPerfilIn(eq(ESCOLA_A), anyCollection())).thenReturn(usuarios);
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoas));
    }

    @Test
    void cadastrarCriaPessoaFuncionarioELoginPeloAcessoInicialComOPerfilPedido() {
        semNinguemNaEquipe();
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(acessoService.enviarAcessoInicial(any(Pessoa.class), eq(PerfilUsuario.FINANCEIRO)))
                .thenAnswer(inv -> usuario(inv.getArgument(0), PerfilUsuario.FINANCEIRO));

        MembroEquipeResponse resposta = equipeService.cadastrar(request(PerfilUsuario.FINANCEIRO, "11122233344"));

        ArgumentCaptor<Pessoa> pessoa = ArgumentCaptor.forClass(Pessoa.class);
        verify(pessoaRepository).save(pessoa.capture());
        assertThat(pessoa.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(pessoa.getValue().getNome()).isEqualTo("Ana Lima");
        assertThat(pessoa.getValue().getEmail()).isEqualTo("ana@escola.com");
        ArgumentCaptor<PessoaPapel> papel = ArgumentCaptor.forClass(PessoaPapel.class);
        verify(pessoaPapelRepository).save(papel.capture());
        assertThat(papel.getValue().getPapel()).isEqualTo(PapelPessoa.FUNCIONARIO);
        assertThat(resposta.perfil()).isEqualTo(PerfilUsuario.FINANCEIRO);
        assertThat(resposta.aguardandoPrimeiroAcesso()).isTrue();
    }

    @Test
    void cadastrarRecusaPerfilForaDaEquipe() {
        for (PerfilUsuario perfil : List.of(PerfilUsuario.MASTER, PerfilUsuario.PROFESSOR, PerfilUsuario.ALUNO,
                PerfilUsuario.SUPER_ADMIN)) {
            assertThatThrownBy(() -> equipeService.cadastrar(request(perfil, "11122233344")))
                    .as(perfil.name()).isInstanceOf(IllegalArgumentException.class);
        }
        verify(pessoaRepository, never()).save(any());
        verify(acessoService, never()).enviarAcessoInicial(any(), any());
    }

    @Test
    void cadastrarRecusaCpfJaUsadoNaEquipeMesmoEmOutroPerfil() {
        comAEquipe(pessoa("Bia", "11122233344"));

        assertThatThrownBy(() -> equipeService.cadastrar(request(PerfilUsuario.FINANCEIRO, "11122233344")))
                .isInstanceOf(CpfJaCadastradoException.class);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void listarConsultaSoAEscolaDoTokenEOsPerfisDaEquipeSemCpfEmOrdemDeNome() {
        comAEquipe(pessoa("Carla", "11122233344"), pessoa("Ana", "55566677788"));

        List<MembroEquipeResponse> lista = equipeService.listar(null, null, null);

        verify(usuarioRepository).findAllByEscolaIdAndPerfilIn(ESCOLA_A, EQUIPE);
        assertThat(lista).extracting(MembroEquipeResponse::nome).containsExactly("Ana", "Carla");
        assertThat(lista).extracting(MembroEquipeResponse::cpf).containsOnlyNulls();
    }

    @Test
    void listarFiltraPorPerfilNomeEAtivo() {
        Pessoa ana = pessoa("Ana Souza", "11122233344");
        Pessoa bia = pessoa("Bia Lima", "55566677788");
        Usuario inativa = usuario(bia, PerfilUsuario.FINANCEIRO);
        inativa.setAtivo(false);
        when(usuarioRepository.findAllByEscolaIdAndPerfilIn(ESCOLA_A, Set.of(PerfilUsuario.FINANCEIRO)))
                .thenReturn(List.of(usuario(ana, PerfilUsuario.FINANCEIRO), inativa));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(ana, bia));

        assertThat(equipeService.listar(PerfilUsuario.FINANCEIRO, "souza", null))
                .extracting(MembroEquipeResponse::nome).containsExactly("Ana Souza");
        assertThat(equipeService.listar(PerfilUsuario.FINANCEIRO, null, false))
                .extracting(MembroEquipeResponse::nome).containsExactly("Bia Lima");
        assertThatThrownBy(() -> equipeService.listar(PerfilUsuario.PROFESSOR, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alguemDeOutraEscolaOuDeOutroPerfilEhTratadoComoInexistente() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findByIdAndEscolaIdAndPerfilIn(id, ESCOLA_A, EQUIPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> equipeService.buscarPorId(id)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> equipeService.inativar(id)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> equipeService.reenviarAcesso(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void atualizarTrocaOsDadosESincronizaOLoginComOEmailNovo() {
        Pessoa ana = pessoa("Ana", "11122233344");
        Usuario usuario = usuario(ana, PerfilUsuario.SECRETARIA);
        when(usuarioRepository.findByIdAndEscolaIdAndPerfilIn(usuario.getId(), ESCOLA_A, EQUIPE))
                .thenReturn(Optional.of(usuario));
        when(pessoaRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findAllByEscolaIdAndPerfilIn(eq(ESCOLA_A), anyCollection()))
                .thenReturn(List.of(usuario));
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> inv.getArgument(0));

        MembroEquipeResponse resposta = equipeService.atualizar(usuario.getId(),
                new MembroEquipeUpdateRequest("Ana Maria", "11122233344", "nova@escola.com", null));

        assertThat(resposta.nome()).isEqualTo("Ana Maria");
        assertThat(resposta.email()).isEqualTo("nova@escola.com");
        verify(acessoService).sincronizarLogin(ana);
    }

    @Test
    void inativarEAtivarTrocamSoOFlagDoUsuario() {
        Pessoa ana = pessoa("Ana", "11122233344");
        Usuario usuario = usuario(ana, PerfilUsuario.FINANCEIRO);
        when(usuarioRepository.findByIdAndEscolaIdAndPerfilIn(usuario.getId(), ESCOLA_A, EQUIPE))
                .thenReturn(Optional.of(usuario));
        when(pessoaRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(equipeService.inativar(usuario.getId()).ativo()).isFalse();
        assertThat(equipeService.ativar(usuario.getId()).ativo()).isTrue();
        assertThat(ana.isAtivo()).isTrue();
    }

    @Test
    void reenviarAcessoUsaOPerfilDaPessoaERecusaQuemEstaInativo() {
        Pessoa ana = pessoa("Ana", "11122233344");
        Usuario usuario = usuario(ana, PerfilUsuario.FINANCEIRO);
        when(usuarioRepository.findByIdAndEscolaIdAndPerfilIn(usuario.getId(), ESCOLA_A, EQUIPE))
                .thenReturn(Optional.of(usuario));
        when(pessoaRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));

        equipeService.reenviarAcesso(usuario.getId());
        verify(acessoService).enviarAcessoInicial(ana, PerfilUsuario.FINANCEIRO);

        usuario.setAtivo(false);
        assertThatThrownBy(() -> equipeService.reenviarAcesso(usuario.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
