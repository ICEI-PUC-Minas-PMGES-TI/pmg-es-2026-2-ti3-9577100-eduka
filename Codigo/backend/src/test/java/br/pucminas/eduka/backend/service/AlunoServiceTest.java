// Codigo/backend/src/test/java/br/pucminas/eduka/backend/service/AlunoServiceTest.java
package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Responsavel;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.dto.EnderecoRequest;
import br.pucminas.eduka.backend.dto.ResponsavelRequest;
import br.pucminas.eduka.backend.exception.AcessoJaAtivadoException;
import br.pucminas.eduka.backend.exception.NumeroMatriculaJaCadastradoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.ResponsavelRepository;
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
    @Mock
    private ResponsavelRepository responsavelRepository;
    @Mock
    private CobrancaRepository cobrancaRepository;
    @Mock
    private AcessoService acessoService;
    @Mock
    private MatriculaTurmaRepository matriculaRepository;

    private AlunoService alunoService;

    @BeforeEach
    void setUp() {
        // IsolamentoEscola real: o ponto e provar que o servico consulta o
        // contexto de seguranca, nao um mock que devolve o que quisermos.
        IsolamentoEscola isolamentoEscola = new IsolamentoEscola();
        ResponsavelService responsavelService = new ResponsavelService(responsavelRepository,
                alunoResponsavelRepository, alunoRepository, pessoaRepository, pessoaPapelRepository,
                cobrancaRepository, isolamentoEscola);
        alunoService = new AlunoService(alunoRepository, pessoaRepository, pessoaPapelRepository,
                responsavelService, isolamentoEscola, acessoService, matriculaRepository);
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

    private static AlunoRequest request(String nome, String matricula, EnderecoRequest endereco,
            List<ResponsavelRequest> responsaveis) {
        return new AlunoRequest(nome, "12345678900", " ana@example.com ", null, endereco, matricula, null,
                responsaveis);
    }

    private void stubAcessoCriado(UUID usuarioId) {
        when(acessoService.enviarAcessoInicial(any(Pessoa.class), eq(PerfilUsuario.ALUNO)))
                .thenReturn(Usuario.builder().id(usuarioId).build());
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

    private static MatriculaTurmaRepository.Vigente vigente(Aluno aluno, StatusMatricula status, long turmaId,
            String turmaNome) {
        return new MatriculaTurmaRepository.Vigente() {
            public UUID getAlunoId() {
                return aluno.getId();
            }

            public StatusMatricula getStatus() {
                return status;
            }

            public Long getTurmaId() {
                return turmaId;
            }

            public String getTurmaNome() {
                return turmaNome;
            }
        };
    }

    @Test
    void listarTrazASituacaoDeCadaAlunoComAAtivaValendoMaisQueATrancada() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana");
        Pessoa pessoaBia = pessoa("Bia");
        Pessoa pessoaCaio = pessoa("Caio");
        Aluno ana = aluno(ESCOLA_A, pessoaAna.getId());
        Aluno bia = aluno(ESCOLA_A, pessoaBia.getId());
        Aluno caio = aluno(ESCOLA_A, pessoaCaio.getId());
        when(alunoRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(ana, bia, caio));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A)))
                .thenReturn(List.of(pessoaAna, pessoaBia, pessoaCaio));
        // Ana tem a trancada antes da ativa; Bia so a trancada; Caio nada.
        when(matriculaRepository.listarVigentes(ESCOLA_A)).thenReturn(List.of(
                vigente(ana, StatusMatricula.TRANCADA, 1L, "1A"),
                vigente(ana, StatusMatricula.ATIVA, 2L, "2B"),
                vigente(bia, StatusMatricula.TRANCADA, 1L, "1A")));

        List<AlunoResponse> resultado = alunoService.listar(null);

        assertThat(resultado).extracting(AlunoResponse::nome, AlunoResponse::situacao, AlunoResponse::turmaId,
                AlunoResponse::turmaNome).containsExactly(
                        tuple("Ana", AlunoResponse.Situacao.MATRICULADO, 2L, "2B"),
                        tuple("Bia", AlunoResponse.Situacao.TRANCADO, 1L, "1A"),
                        tuple("Caio", AlunoResponse.Situacao.SEM_TURMA, null, null));
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
        stubAcessoCriado(UUID.randomUUID());

        AlunoResponse resultado = alunoService.cadastrar(request("Ana", "2026-001", null, null));

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
        stubAcessoCriado(UUID.randomUUID());

        when(responsavelRepository.save(any(Responsavel.class))).thenAnswer(inv -> {
            Responsavel responsavel = inv.getArgument(0);
            responsavel.setId(UUID.randomUUID());
            return responsavel;
        });

        AlunoResponse resultado = alunoService.cadastrar(request("Ana", "2026-001", null,
                List.of(new ResponsavelRequest("Maria Silva", "52998224725", "maria@example.com", "31999998888", "MAE", true))));

        assertThat(resultado.responsaveis()).hasSize(1);
        assertThat(resultado.responsaveis().get(0).nome()).isEqualTo("Maria Silva");
        assertThat(resultado.responsaveis().get(0).email()).isEqualTo("maria@example.com");
        assertThat(resultado.responsaveis().get(0).telefone()).isEqualTo("31999998888");
        assertThat(resultado.responsaveis().get(0).parentesco()).isEqualTo("MAE");
        assertThat(resultado.responsaveis().get(0).financeiro()).isTrue();
        assertThat(resultado.responsaveis().get(0).responsavelId()).isNotNull();

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
        assertThat(vinculo.isFinanceiro()).isTrue();

        ArgumentCaptor<Responsavel> responsavelCaptor = ArgumentCaptor.forClass(Responsavel.class);
        verify(responsavelRepository).save(responsavelCaptor.capture());
        assertThat(responsavelCaptor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(responsavelCaptor.getValue().getPessoaId()).isEqualTo(pessoaResponsavelId);
    }

    @Test
    void cadastrarComCpfDeResponsavelExistenteReaproveitaOCadastro() {
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
        stubAcessoCriado(UUID.randomUUID());
        Pessoa maeExistente = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Maria Silva")
                .cpf("52998224725").ativo(true).build();
        Responsavel existente = Responsavel.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A)
                .pessoaId(maeExistente.getId()).build();
        when(responsavelRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(existente));
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(maeExistente.getId()), ESCOLA_A))
                .thenReturn(List.of(maeExistente));

        AlunoResponse resultado = alunoService.cadastrar(request("Ana", "2026-001", null,
                List.of(new ResponsavelRequest("Outro Nome", "52998224725", null, null, "MAE", null))));

        assertThat(resultado.responsaveis()).singleElement().satisfies(r -> {
            assertThat(r.responsavelId()).isEqualTo(existente.getId());
            assertThat(r.nome()).isEqualTo("Maria Silva");
            assertThat(r.financeiro()).isFalse();
        });
        verify(responsavelRepository, never()).save(any());
        verify(pessoaRepository, times(1)).save(any(Pessoa.class));
    }

    @Test
    void cadastrarComDoisResponsaveisFinanceirosRecusa() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> inv.getArgument(0));
        stubAcessoCriado(UUID.randomUUID());

        assertThatThrownBy(() -> alunoService.cadastrar(request("Ana", "2026-001", null, List.of(
                new ResponsavelRequest("Maria", null, null, null, "MAE", true),
                new ResponsavelRequest("Joao", null, null, null, "PAI", true)))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(alunoResponsavelRepository, never()).save(any());
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
        stubAcessoCriado(UUID.randomUUID());

        AlunoResponse resultado = alunoService.cadastrar(request("Ana", "2026-001",
                new EnderecoRequest(null, null, null, "Belo Horizonte", null, null), null));

        assertThat(resultado.endereco().cidade()).isEqualTo("Belo Horizonte");
        assertThat(resultado.endereco().logradouro()).isNull();
    }

    @Test
    void cadastrarSemNomeOuMatriculaEhRejeitado() {
        autenticarComo("SECRETARIA", ESCOLA_A);

        assertThatThrownBy(
                () -> alunoService.cadastrar(request(" ", "2026-001", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> alunoService.cadastrar(request("Ana", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(alunoRepository, never()).save(any());
        verify(pessoaRepository, never()).save(any());
        verify(acessoService, never()).enviarAcessoInicial(any(), any());
    }

    @Test
    void cadastrarCriaOLoginDoAlunoEGuardaOVinculo() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID usuarioId = UUID.randomUUID();
        stubAcessoCriado(usuarioId);

        AlunoResponse resultado = alunoService.cadastrar(request("Ana", "2026-001", null, null));

        ArgumentCaptor<Pessoa> pessoaCaptor = ArgumentCaptor.forClass(Pessoa.class);
        verify(acessoService).enviarAcessoInicial(pessoaCaptor.capture(), eq(PerfilUsuario.ALUNO));
        assertThat(pessoaCaptor.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(pessoaCaptor.getValue().getCpf()).isEqualTo("12345678900");

        ArgumentCaptor<Aluno> alunoCaptor = ArgumentCaptor.forClass(Aluno.class);
        verify(alunoRepository).save(alunoCaptor.capture());
        assertThat(alunoCaptor.getValue().getUsuarioId()).isEqualTo(usuarioId);
        assertThat(resultado.email()).isEqualTo("ana@example.com");
    }

    @Test
    void reenviarAcessoRenovaOAcessoDoAlunoDaEscola() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana");
        Aluno ana = aluno(ESCOLA_A, pessoaAna.getId());
        UUID usuarioId = UUID.randomUUID();
        ana.setUsuarioId(usuarioId);
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        stubAcessoCriado(usuarioId);

        alunoService.reenviarAcesso(ana.getId());

        verify(acessoService).enviarAcessoInicial(pessoaAna, PerfilUsuario.ALUNO);
        verify(alunoRepository, never()).save(any());
    }

    @Test
    void reenviarAcessoDeAlunoSemLoginCriaEGuardaOVinculo() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana");
        Aluno ana = aluno(ESCOLA_A, pessoaAna.getId());
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        UUID usuarioId = UUID.randomUUID();
        stubAcessoCriado(usuarioId);

        alunoService.reenviarAcesso(ana.getId());

        verify(alunoRepository).save(ana);
        assertThat(ana.getUsuarioId()).isEqualTo(usuarioId);
    }

    @Test
    void reenviarAcessoDeQuemJaDefiniuASenhaPropagaOConflito() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana");
        Aluno ana = aluno(ESCOLA_A, pessoaAna.getId());
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(acessoService.enviarAcessoInicial(pessoaAna, PerfilUsuario.ALUNO))
                .thenThrow(new AcessoJaAtivadoException());

        assertThatThrownBy(() -> alunoService.reenviarAcesso(ana.getId()))
                .isInstanceOf(AcessoJaAtivadoException.class);
    }

    @Test
    void reenviarAcessoDeAlunoDeOutraEscolaEhTratadoComoInexistente() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Aluno daEscolaB = aluno(ESCOLA_B, UUID.randomUUID());
        when(alunoRepository.findByIdAndEscolaId(daEscolaB.getId(), ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alunoService.reenviarAcesso(daEscolaB.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(acessoService, never()).enviarAcessoInicial(any(), any());
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

    @Test
    void cadastrarComNumeroDeMatriculaRepetidoNaEscolaResponde409SemCriarLogin() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(alunoRepository.existsByEscolaIdAndMatricula(ESCOLA_A, "2026-001")).thenReturn(true);

        assertThatThrownBy(() -> alunoService.cadastrar(request("Ana", " 2026-001 ", null, null)))
                .isInstanceOf(NumeroMatriculaJaCadastradoException.class);
        verify(pessoaRepository, never()).save(any());
        verify(acessoService, never()).enviarAcessoInicial(any(), any());
    }

    @Test
    void atualizarSubstituiOsDadosESincronizaOLogin() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana");
        pessoaAna.setCidade("Contagem");
        Aluno ana = aluno(ESCOLA_A, pessoaAna.getId());
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> inv.getArgument(0));

        AlunoResponse resposta = alunoService.atualizar(ana.getId(),
                request("Ana Maria ", "2026-002", null, null));

        assertThat(resposta.nome()).isEqualTo("Ana Maria");
        assertThat(resposta.email()).isEqualTo("ana@example.com");
        assertThat(resposta.matricula()).isEqualTo("2026-002");
        // Endereco nulo no request apaga o anterior.
        assertThat(resposta.endereco()).isNull();
        assertThat(ana.getAtualizadoEm()).isNotNull();
        verify(acessoService).sincronizarLogin(pessoaAna);
        verify(alunoResponsavelRepository, never()).save(any());
    }

    @Test
    void atualizarComMatriculaDeOutroAlunoResponde409SemGravar() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana");
        Aluno ana = aluno(ESCOLA_A, pessoaAna.getId());
        when(alunoRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(alunoRepository.existsByEscolaIdAndMatriculaAndIdNot(ESCOLA_A, "2026-009", ana.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> alunoService.atualizar(ana.getId(), request("Ana", "2026-009", null, null)))
                .isInstanceOf(NumeroMatriculaJaCadastradoException.class);
        verify(pessoaRepository, never()).save(any());
        verify(alunoRepository, never()).save(any());
    }

    @Test
    void atualizarAlunoDeOutraEscolaEhTratadoComoInexistente() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        UUID idDaEscolaB = UUID.randomUUID();
        when(alunoRepository.findByIdAndEscolaId(idDaEscolaB, ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alunoService.atualizar(idDaEscolaB, request("Ana", "2026-001", null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void buscarMeusDadosUsaOLoginEAEscolaDoToken() {
        UUID usuarioId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(usuarioId, "ALUNO", ESCOLA_A), null, "ROLE_ALUNO"));
        Pessoa pessoaAna = pessoa("Ana");
        Aluno ana = aluno(ESCOLA_A, pessoaAna.getId());
        when(alunoRepository.findByUsuarioIdAndEscolaId(usuarioId, ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));

        AlunoResponse resposta = alunoService.buscarMeusDados();

        assertThat(resposta.id()).isEqualTo(ana.getId());
        assertThat(resposta.nome()).isEqualTo("Ana");
        assertThat(resposta.cpf()).isEqualTo("12345678900");
    }

    @Test
    void buscarMeusDadosDeLoginSemCadastroDeAlunoResponde404() {
        autenticarComo("ALUNO", ESCOLA_A);
        when(alunoRepository.findByUsuarioIdAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alunoService.buscarMeusDados())
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(pessoaRepository, never()).findByIdAndEscolaId(any(), any());
    }
}
