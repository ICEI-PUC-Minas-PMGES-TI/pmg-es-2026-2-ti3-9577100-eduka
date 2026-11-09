package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.domain.VinculoProfessor;
import br.pucminas.eduka.backend.dto.ProfessorRequest;
import br.pucminas.eduka.backend.dto.ProfessorResponse;
import br.pucminas.eduka.backend.exception.AcessoJaAtivadoException;
import br.pucminas.eduka.backend.exception.CpfJaCadastradoException;
import br.pucminas.eduka.backend.exception.ProfessorComHistoricoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Isolamento por escola no servico de professor: toda consulta e escrita usa
 * a escola do token. Cobre tambem o CPF unico por escola (checado em memoria,
 * ja que a coluna e cifrada), inativacao e exclusao e a criacao do login
 * junto com o cadastro.
 */
@ExtendWith(MockitoExtension.class)
class ProfessorServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();

    @Mock
    private ProfessorRepository professorRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;
    @Mock
    private AcessoService acessoService;

    private ProfessorService professorService;

    @BeforeEach
    void setUp() {
        professorService = new ProfessorService(professorRepository, pessoaRepository, pessoaPapelRepository,
                new IsolamentoEscola(), acessoService, auditoriaService);
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

    private static Professor professor(UUID escolaId, UUID pessoaId) {
        return Professor.builder().id(UUID.randomUUID()).escolaId(escolaId).pessoaId(pessoaId)
                .formacao("Licenciatura em Matematica").ativo(true).build();
    }

    private static Pessoa pessoa(String nome, String cpf) {
        return Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome(nome).cpf(cpf).ativo(true).build();
    }

    private static ProfessorRequest request(String nome, String cpf) {
        return new ProfessorRequest(nome, cpf, "prof@example.com", "31999998888", "Licenciatura em Fisica",
                LocalDate.of(2026, 2, 1), VinculoProfessor.CLT_40H);
    }

    private void stubSaves() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            if (pessoa.getId() == null) {
                pessoa.setId(UUID.randomUUID());
            }
            return pessoa;
        });
        when(professorRepository.save(any(Professor.class))).thenAnswer(inv -> {
            Professor professor = inv.getArgument(0);
            if (professor.getId() == null) {
                professor.setId(UUID.randomUUID());
            }
            return professor;
        });
    }

    @Test
    void listarFiltraPelaEscolaDoTokenENaoVazaCpf() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        when(professorRepository.findAllByEscolaId(ESCOLA_A))
                .thenReturn(List.of(professor(ESCOLA_A, pessoaAna.getId())));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));

        List<ProfessorResponse> resultado = professorService.listar(null, null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nome()).isEqualTo("Ana");
        assertThat(resultado.get(0).cpf()).isNull();
        verify(professorRepository, never()).findAll();
        verify(pessoaRepository, never()).findAllById(any());
    }

    @Test
    void listarFiltraPorNomeParcialEPorAtivo() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana Silva", "11111111111");
        Pessoa pessoaAndre = pessoa("Andre Souza", "22222222222");
        Pessoa pessoaCarlos = pessoa("Carlos Lima", "33333333333");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        Professor andre = professor(ESCOLA_A, pessoaAndre.getId());
        andre.setAtivo(false);
        Professor carlos = professor(ESCOLA_A, pessoaCarlos.getId());
        when(professorRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(ana, andre, carlos));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A)))
                .thenReturn(List.of(pessoaAna, pessoaAndre, pessoaCarlos));

        assertThat(professorService.listar(" AN ", null)).extracting(ProfessorResponse::nome)
                .containsExactly("Ana Silva", "Andre Souza");
        assertThat(professorService.listar("an", true)).extracting(ProfessorResponse::nome)
                .containsExactly("Ana Silva");
        assertThat(professorService.listar(null, false)).extracting(ProfessorResponse::nome)
                .containsExactly("Andre Souza");
    }

    @Test
    void buscarPorIdDevolveCpfEConsultaComEscolaDoToken() {
        autenticarComo("MASTER", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Professor esperado = professor(ESCOLA_A, pessoaAna.getId());
        when(professorRepository.findByIdAndEscolaId(esperado.getId(), ESCOLA_A)).thenReturn(Optional.of(esperado));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));

        ProfessorResponse resultado = professorService.buscarPorId(esperado.getId());

        assertThat(resultado.cpf()).isEqualTo("12345678900");
        assertThat(resultado.formacao()).isEqualTo("Licenciatura em Matematica");
        verify(professorRepository, never()).findById(any());
        verify(pessoaRepository, never()).findById(any());
    }

    @Test
    void professorDeOutraEscolaEhTratadoComoInexistente() {
        autenticarComo("MASTER", ESCOLA_A);
        Professor daEscolaB = professor(ESCOLA_B, UUID.randomUUID());
        when(professorRepository.findByIdAndEscolaId(daEscolaB.getId(), ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professorService.buscarPorId(daEscolaB.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> professorService.atualizar(daEscolaB.getId(), request("Ana", "12345678900")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> professorService.inativar(daEscolaB.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> professorService.excluir(daEscolaB.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(professorRepository, never()).save(any());
        verify(professorRepository, never()).delete(any());
        verify(pessoaRepository, never()).save(any());
    }

    private void stubAcessoCriado(UUID usuarioId) {
        when(acessoService.enviarAcessoInicial(any(Pessoa.class), eq(PerfilUsuario.PROFESSOR)))
                .thenReturn(Usuario.builder().id(usuarioId).build());
    }

    @Test
    void cadastrarGravaEscolaDoTokenEPapelProfessor() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(professorRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of());
        stubSaves();
        stubAcessoCriado(UUID.randomUUID());

        ProfessorResponse resultado = professorService.cadastrar(request(" Ana ", "12345678900"));

        ArgumentCaptor<Pessoa> pessoaCaptor = ArgumentCaptor.forClass(Pessoa.class);
        verify(pessoaRepository).save(pessoaCaptor.capture());
        assertThat(pessoaCaptor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(pessoaCaptor.getValue().getNome()).isEqualTo("Ana");

        ArgumentCaptor<PessoaPapel> papelCaptor = ArgumentCaptor.forClass(PessoaPapel.class);
        verify(pessoaPapelRepository).save(papelCaptor.capture());
        assertThat(papelCaptor.getValue().getPapel()).isEqualTo(PapelPessoa.PROFESSOR);
        assertThat(papelCaptor.getValue().getPessoaId()).isEqualTo(pessoaCaptor.getValue().getId());

        ArgumentCaptor<Professor> professorCaptor = ArgumentCaptor.forClass(Professor.class);
        verify(professorRepository).save(professorCaptor.capture());
        assertThat(professorCaptor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(professorCaptor.getValue().getPessoaId()).isEqualTo(pessoaCaptor.getValue().getId());
        assertThat(professorCaptor.getValue().getVinculo()).isEqualTo(VinculoProfessor.CLT_40H);

        assertThat(resultado.escolaId()).isEqualTo(ESCOLA_A);
        assertThat(resultado.ativo()).isTrue();
        assertThat(resultado.dataAdmissao()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(resultado.vinculo()).isEqualTo(VinculoProfessor.CLT_40H);
    }

    @Test
    void cadastrarCriaOLoginDoProfessorEGuardaOVinculo() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        when(professorRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of());
        stubSaves();
        UUID usuarioId = UUID.randomUUID();
        stubAcessoCriado(usuarioId);

        professorService.cadastrar(request("Ana", "12345678900"));

        ArgumentCaptor<Pessoa> pessoaCaptor = ArgumentCaptor.forClass(Pessoa.class);
        verify(acessoService).enviarAcessoInicial(pessoaCaptor.capture(), eq(PerfilUsuario.PROFESSOR));
        assertThat(pessoaCaptor.getValue().getEmail()).isEqualTo("prof@example.com");
        assertThat(pessoaCaptor.getValue().getCpf()).isEqualTo("12345678900");

        ArgumentCaptor<Professor> professorCaptor = ArgumentCaptor.forClass(Professor.class);
        verify(professorRepository).save(professorCaptor.capture());
        assertThat(professorCaptor.getValue().getUsuarioId()).isEqualTo(usuarioId);
    }

    @Test
    void reenviarAcessoRenovaOAcessoDoProfessorDaEscola() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        UUID usuarioId = UUID.randomUUID();
        ana.setUsuarioId(usuarioId);
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        stubAcessoCriado(usuarioId);

        professorService.reenviarAcesso(ana.getId());

        verify(acessoService).enviarAcessoInicial(pessoaAna, PerfilUsuario.PROFESSOR);
        verify(professorRepository, never()).save(any());
    }

    @Test
    void reenviarAcessoParaProfessorInativoEhRecusado() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Professor ana = professor(ESCOLA_A, UUID.randomUUID());
        ana.setAtivo(false);
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> professorService.reenviarAcesso(ana.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(acessoService, never()).enviarAcessoInicial(any(), any());
    }

    @Test
    void reenviarAcessoDeQuemJaDefiniuASenhaPropagaOConflito() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(acessoService.enviarAcessoInicial(pessoaAna, PerfilUsuario.PROFESSOR))
                .thenThrow(new AcessoJaAtivadoException());

        assertThatThrownBy(() -> professorService.reenviarAcesso(ana.getId()))
                .isInstanceOf(AcessoJaAtivadoException.class);
    }

    @Test
    void cadastrarComCpfJaUsadoNaEscolaEhRejeitado() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa existente = pessoa("Ana", "12345678900");
        when(professorRepository.findAllByEscolaId(ESCOLA_A))
                .thenReturn(List.of(professor(ESCOLA_A, existente.getId())));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(existente));

        assertThatThrownBy(() -> professorService.cadastrar(request("Outra Ana", "12345678900")))
                .isInstanceOf(CpfJaCadastradoException.class);
        verify(pessoaRepository, never()).save(any());
        verify(professorRepository, never()).save(any());
    }

    @Test
    void atualizarMantemOProprioCpfESubstituiOsDados() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(professorRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(ana));
        stubSaves();

        ProfessorResponse resultado = professorService.atualizar(ana.getId(),
                new ProfessorRequest("Ana Maria", "12345678900", " ana@example.com ", null, "Mestrado em Fisica",
                        null, VinculoProfessor.HORISTA));

        assertThat(resultado.nome()).isEqualTo("Ana Maria");
        assertThat(resultado.email()).isEqualTo("ana@example.com");
        assertThat(resultado.telefone()).isNull();
        verify(acessoService).sincronizarLogin(pessoaAna);
        assertThat(resultado.formacao()).isEqualTo("Mestrado em Fisica");
        assertThat(resultado.vinculo()).isEqualTo(VinculoProfessor.HORISTA);
        assertThat(ana.getAtualizadoEm()).isNotNull();
        assertThat(ana.getEscolaId()).isEqualTo(ESCOLA_A);
    }

    @Test
    void atualizarParaCpfDeOutroProfessorDaEscolaEhRejeitado() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "11111111111");
        Pessoa pessoaCarlos = pessoa("Carlos", "22222222222");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        Professor carlos = professor(ESCOLA_A, pessoaCarlos.getId());
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(professorRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(ana, carlos));
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(pessoaCarlos.getId()), ESCOLA_A))
                .thenReturn(List.of(pessoaCarlos));

        assertThatThrownBy(() -> professorService.atualizar(ana.getId(), request("Ana", "22222222222")))
                .isInstanceOf(CpfJaCadastradoException.class);
        assertThat(pessoaAna.getCpf()).isEqualTo("11111111111");
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void inativarEAtivarSoTrocamOFlag() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(professorRepository.save(any(Professor.class))).thenAnswer(inv -> inv.getArgument(0));

        professorService.inativar(ana.getId());
        assertThat(ana.isAtivo()).isFalse();
        verify(acessoService).definirAtivo(pessoaAna, false);

        assertThat(professorService.ativar(ana.getId()).ativo()).isTrue();
        verify(acessoService).definirAtivo(pessoaAna, true);
        verify(professorRepository, never()).delete(any());
        verify(professorRepository, never()).deleteById(any());
    }

    @Test
    void excluirApagaProfessorLoginPapelEPessoa() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        PessoaPapel papel = PessoaPapel.builder().pessoaId(pessoaAna.getId()).papel(PapelPessoa.PROFESSOR).build();
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(pessoaPapelRepository.findAllByPessoaId(pessoaAna.getId())).thenReturn(List.of(papel));

        professorService.excluir(ana.getId());

        verify(professorRepository).delete(ana);
        verify(acessoService).excluirLogin(pessoaAna);
        verify(pessoaPapelRepository).deleteAll(List.of(papel));
        verify(pessoaRepository).delete(pessoaAna);
    }

    @Test
    void excluirMantemPessoaQueTemOutroPapelNaEscola() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        PessoaPapel professora = PessoaPapel.builder().pessoaId(pessoaAna.getId()).papel(PapelPessoa.PROFESSOR)
                .build();
        PessoaPapel responsavel = PessoaPapel.builder().pessoaId(pessoaAna.getId())
                .papel(PapelPessoa.RESPONSAVEL).build();
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(pessoaPapelRepository.findAllByPessoaId(pessoaAna.getId())).thenReturn(List.of(professora, responsavel));

        professorService.excluir(ana.getId());

        verify(professorRepository).delete(ana);
        verify(pessoaPapelRepository).deleteAll(List.of(professora));
        verify(pessoaRepository, never()).delete(any());
    }

    @Test
    void excluirProfessorComHistoricoEhRecusado() {
        autenticarComo("SECRETARIA", ESCOLA_A);
        Pessoa pessoaAna = pessoa("Ana", "12345678900");
        Professor ana = professor(ESCOLA_A, pessoaAna.getId());
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        doThrow(new DataIntegrityViolationException("fk")).when(professorRepository).flush();

        assertThatThrownBy(() -> professorService.excluir(ana.getId()))
                .isInstanceOf(ProfessorComHistoricoException.class);
        verify(acessoService, never()).excluirLogin(any());
        verify(pessoaRepository, never()).delete(any());
    }

    @Test
    void semUsuarioAutenticadoNadaEhConsultado() {
        assertThatThrownBy(() -> professorService.listar(null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> professorService.buscarPorId(UUID.randomUUID()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verify(professorRepository, never()).findAllByEscolaId(any());
        verify(professorRepository, never()).findByIdAndEscolaId(any(), any());
    }
}
