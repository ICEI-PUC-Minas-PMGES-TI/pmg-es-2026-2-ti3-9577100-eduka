package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.SecretariaRequest;
import br.pucminas.eduka.backend.dto.SecretariaResponse;
import br.pucminas.eduka.backend.dto.SecretariaUpdateRequest;
import br.pucminas.eduka.backend.exception.CpfJaCadastradoException;
import br.pucminas.eduka.backend.exception.LoginJaCadastradoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.SenhaInvalidaException;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PasswordEncoderConfig;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Isolamento por escola no servico de secretaria: toda consulta e escrita usa
 * a escola do token e filtra por {@code PerfilUsuario.SECRETARIA}. Cobre
 * tambem o CPF unico por escola (checado em memoria, ja que a coluna e
 * cifrada), o hash de senha e a inativacao (flag em {@link Usuario}, nao em
 * {@link Pessoa}) no lugar da exclusao.
 */
@ExtendWith(MockitoExtension.class)
class SecretariaServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String SENHA = "senha-forte-123";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;

    // Encoder real: o ponto do teste e garantir que o hash e gerado de verdade.
    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private SecretariaService secretariaService;

    @BeforeEach
    void setUp() {
        secretariaService = new SecretariaService(usuarioRepository, pessoaRepository, pessoaPapelRepository,
                passwordEncoder, new IsolamentoEscola());
        autenticarComo("MASTER", ESCOLA_A);
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

    private static Usuario secretaria(UUID escolaId, UUID pessoaId) {
        return Usuario.builder().id(UUID.randomUUID()).escolaId(escolaId).pessoaId(pessoaId)
                .login("secretaria" + UUID.randomUUID()).senhaHash("hash").perfil(PerfilUsuario.SECRETARIA)
                .ativo(true).build();
    }

    private static Pessoa pessoa(String nome, String cpf) {
        return Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome(nome).cpf(cpf).ativo(true).build();
    }

    private static SecretariaRequest request(String nome, String cpf, String login) {
        return new SecretariaRequest(nome, cpf, "secretaria@example.com", "31999998888", login, SENHA);
    }

    private void stubSaves() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            if (pessoa.getId() == null) {
                pessoa.setId(UUID.randomUUID());
            }
            return pessoa;
        });
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario usuario = inv.getArgument(0);
            if (usuario.getId() == null) {
                usuario.setId(UUID.randomUUID());
            }
            return usuario;
        });
    }

    @Test
    void cadastrarPersistsHashInsteadOfPlainPassword() {
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA)).thenReturn(List.of());
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
        stubSaves();

        secretariaService.cadastrar(request("Maria", "12345678900", "maria"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        String senhaHash = captor.getValue().getSenhaHash();

        assertThat(senhaHash).isNotEqualTo(SENHA);
        assertThat(passwordEncoder.matches(SENHA, senhaHash)).isTrue();
    }

    @Test
    void cadastrarCopiaCamposEDefinePerfilEAtivo() {
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA)).thenReturn(List.of());
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
        stubSaves();

        SecretariaResponse resultado = secretariaService.cadastrar(request(" Maria ", "12345678900", "maria"));

        ArgumentCaptor<Pessoa> pessoaCaptor = ArgumentCaptor.forClass(Pessoa.class);
        verify(pessoaRepository).save(pessoaCaptor.capture());
        assertThat(pessoaCaptor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(pessoaCaptor.getValue().getNome()).isEqualTo("Maria");

        ArgumentCaptor<PessoaPapel> papelCaptor = ArgumentCaptor.forClass(PessoaPapel.class);
        verify(pessoaPapelRepository).save(papelCaptor.capture());
        assertThat(papelCaptor.getValue().getPapel()).isEqualTo(PapelPessoa.FUNCIONARIO);
        assertThat(papelCaptor.getValue().getPessoaId()).isEqualTo(pessoaCaptor.getValue().getId());

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(usuarioCaptor.getValue().getPessoaId()).isEqualTo(pessoaCaptor.getValue().getId());
        assertThat(usuarioCaptor.getValue().getPerfil()).isEqualTo(PerfilUsuario.SECRETARIA);
        assertThat(usuarioCaptor.getValue().isAtivo()).isTrue();

        assertThat(resultado.escolaId()).isEqualTo(ESCOLA_A);
        assertThat(resultado.ativo()).isTrue();
        assertThat(resultado.login()).isEqualTo("maria");
    }

    @Test
    void cadastrarNormalizaLoginAntesDeChecarEGravar() {
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA)).thenReturn(List.of());
        when(usuarioRepository.existsByLogin("maria@escola.com")).thenReturn(false);
        stubSaves();

        SecretariaResponse resultado = secretariaService
                .cadastrar(request("Maria", "12345678900", "  Maria@Escola.COM "));

        assertThat(resultado.login()).isEqualTo("maria@escola.com");
        verify(usuarioRepository).existsByLogin("maria@escola.com");
    }

    @Test
    void cadastrarRejeitaLoginDuplicado() {
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA)).thenReturn(List.of());
        when(usuarioRepository.existsByLogin("maria")).thenReturn(true);

        assertThatThrownBy(() -> secretariaService.cadastrar(request("Maria", "12345678900", "maria")))
                .isInstanceOf(LoginJaCadastradoException.class);

        verify(usuarioRepository, never()).save(any());
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void cadastrarRejeitaCpfJaUsadoNaEscola() {
        Pessoa existente = pessoa("Ana", "12345678900");
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(List.of(secretaria(ESCOLA_A, existente.getId())));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(existente));

        assertThatThrownBy(() -> secretariaService.cadastrar(request("Outra Ana", "12345678900", "outra")))
                .isInstanceOf(CpfJaCadastradoException.class);

        verify(pessoaRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cpfDeOutraEscolaOuDeOutroPerfilNaoBloqueiaCadastro() {
        // findAllByEscolaIdAndPerfil ja filtra pela escola e pelo perfil SECRETARIA;
        // um CPF repetido em outra escola ou pertencente a outro perfil (ex.:
        // professor) nunca aparece nesta lista, logo nunca colide.
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA)).thenReturn(List.of());
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
        stubSaves();

        SecretariaResponse resultado = secretariaService.cadastrar(request("Maria", "12345678900", "maria"));

        assertThat(resultado).isNotNull();
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void cadastrarRejeitaSenhaCurta() {
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA)).thenReturn(List.of());
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);

        assertThatThrownBy(() -> secretariaService
                .cadastrar(new SecretariaRequest("Maria", "12345678900", null, null, "maria", "1234567")))
                .isInstanceOf(SenhaInvalidaException.class);

        verify(usuarioRepository, never()).save(any());
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void cadastrarRejeitaSenhaMaiorQueLimiteDoBcrypt() {
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA)).thenReturn(List.of());
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
        String senha73 = "a".repeat(73);

        assertThatThrownBy(() -> secretariaService
                .cadastrar(new SecretariaRequest("Maria", "12345678900", null, null, "maria", senha73)))
                .isInstanceOf(SenhaInvalidaException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void listarFiltraPelaEscolaDoTokenENaoVazaCpf() {
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(List.of(secretaria(ESCOLA_A, pessoaAna.getId())));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));

        List<SecretariaResponse> resultado = secretariaService.listar(null, null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nome()).isEqualTo("Ana");
        assertThat(resultado.get(0).cpf()).isNull();
    }

    @Test
    void listarFiltraPorNomeParcialEPorAtivo() {
        Pessoa pessoaAna = pessoa("Ana Silva", "11111111111");
        Pessoa pessoaAndre = pessoa("Andre Souza", "22222222222");
        Pessoa pessoaCarlos = pessoa("Carlos Lima", "33333333333");
        Usuario ana = secretaria(ESCOLA_A, pessoaAna.getId());
        Usuario andre = secretaria(ESCOLA_A, pessoaAndre.getId());
        andre.setAtivo(false);
        Usuario carlos = secretaria(ESCOLA_A, pessoaCarlos.getId());
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(List.of(ana, andre, carlos));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A)))
                .thenReturn(List.of(pessoaAna, pessoaAndre, pessoaCarlos));

        assertThat(secretariaService.listar(" AN ", null)).extracting(SecretariaResponse::nome)
                .containsExactly("Ana Silva", "Andre Souza");
        assertThat(secretariaService.listar("an", true)).extracting(SecretariaResponse::nome)
                .containsExactly("Ana Silva");
        assertThat(secretariaService.listar(null, false)).extracting(SecretariaResponse::nome)
                .containsExactly("Andre Souza");
    }

    @Test
    void buscarPorIdDevolveCpfEConsultaComEscolaDoToken() {
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Usuario esperado = secretaria(ESCOLA_A, pessoaAna.getId());
        when(usuarioRepository.findByIdAndEscolaIdAndPerfil(esperado.getId(), ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(Optional.of(esperado));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));

        SecretariaResponse resultado = secretariaService.buscarPorId(esperado.getId());

        assertThat(resultado.cpf()).isEqualTo("12345678900");
        assertThat(resultado.login()).isEqualTo(esperado.getLogin());
    }

    @Test
    void secretariaDeOutraEscolaOuOutroPerfilEhTratadaComoInexistente() {
        UUID idInexistenteParaEscolaA = UUID.randomUUID();
        when(usuarioRepository.findByIdAndEscolaIdAndPerfil(idInexistenteParaEscolaA, ESCOLA_A,
                PerfilUsuario.SECRETARIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> secretariaService.buscarPorId(idInexistenteParaEscolaA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> secretariaService.atualizar(idInexistenteParaEscolaA,
                new SecretariaUpdateRequest("Ana", "12345678900", null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> secretariaService.inativar(idInexistenteParaEscolaA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> secretariaService.ativar(idInexistenteParaEscolaA))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(usuarioRepository, never()).save(any());
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void atualizarMantemOProprioCpfESubstituiOsDados() {
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Usuario ana = secretaria(ESCOLA_A, pessoaAna.getId());
        when(usuarioRepository.findByIdAndEscolaIdAndPerfil(ana.getId(), ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(List.of(ana));
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> inv.getArgument(0));

        SecretariaResponse resultado = secretariaService.atualizar(ana.getId(),
                new SecretariaUpdateRequest("Ana Maria", "12345678900", null, null));

        assertThat(resultado.nome()).isEqualTo("Ana Maria");
        assertThat(resultado.email()).isNull();
        assertThat(pessoaAna.getEscolaId()).isEqualTo(ESCOLA_A);
    }

    @Test
    void atualizarParaCpfDeOutraSecretariaDaEscolaEhRejeitado() {
        Pessoa pessoaAna = pessoa("Ana", "11111111111");
        Pessoa pessoaCarlos = pessoa("Carlos", "22222222222");
        Usuario ana = secretaria(ESCOLA_A, pessoaAna.getId());
        Usuario carlos = secretaria(ESCOLA_A, pessoaCarlos.getId());
        when(usuarioRepository.findByIdAndEscolaIdAndPerfil(ana.getId(), ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(usuarioRepository.findAllByEscolaIdAndPerfil(ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(List.of(ana, carlos));
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(pessoaCarlos.getId()), ESCOLA_A))
                .thenReturn(List.of(pessoaCarlos));

        assertThatThrownBy(() -> secretariaService.atualizar(ana.getId(),
                new SecretariaUpdateRequest("Ana", "22222222222", null, null)))
                .isInstanceOf(CpfJaCadastradoException.class);
        assertThat(pessoaAna.getCpf()).isEqualTo("11111111111");
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void inativarEAtivarSoTrocamOFlagDoUsuarioNaoDaPessoa() {
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        boolean ativoOriginalDaPessoa = pessoaAna.isAtivo();
        Usuario ana = secretaria(ESCOLA_A, pessoaAna.getId());
        when(usuarioRepository.findByIdAndEscolaIdAndPerfil(ana.getId(), ESCOLA_A, PerfilUsuario.SECRETARIA))
                .thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        secretariaService.inativar(ana.getId());
        assertThat(ana.isAtivo()).isFalse();
        assertThat(pessoaAna.isAtivo()).isEqualTo(ativoOriginalDaPessoa);

        assertThat(secretariaService.ativar(ana.getId()).ativo()).isTrue();
        assertThat(pessoaAna.isAtivo()).isEqualTo(ativoOriginalDaPessoa);
        verify(usuarioRepository, never()).delete(any());
        verify(usuarioRepository, never()).deleteById(any());
        verify(pessoaRepository, never()).save(any());
    }
}
