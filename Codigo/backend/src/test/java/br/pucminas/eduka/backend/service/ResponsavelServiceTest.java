package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;
import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Responsavel;
import br.pucminas.eduka.backend.dto.CadastroResponsavelRequest;
import br.pucminas.eduka.backend.dto.ResponsavelRequest;
import br.pucminas.eduka.backend.dto.CadastroResponsavelResponse;
import br.pucminas.eduka.backend.dto.ResponsavelResponse;
import br.pucminas.eduka.backend.dto.VinculoResponsavelRequest;
import br.pucminas.eduka.backend.exception.CpfJaCadastradoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.RegistroEmUsoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ResponsavelRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

@ExtendWith(MockitoExtension.class)
class ResponsavelServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String CPF = "52998224725";

    @Mock
    private ResponsavelRepository responsavelRepository;
    @Mock
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;
    @Mock
    private CobrancaRepository cobrancaRepository;

    private ResponsavelService service;

    @BeforeEach
    void setUp() {
        service = new ResponsavelService(responsavelRepository, alunoResponsavelRepository, alunoRepository,
                pessoaRepository, pessoaPapelRepository, cobrancaRepository, new IsolamentoEscola());
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), "FINANCEIRO", ESCOLA_A);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(usuario, null, "ROLE_FINANCEIRO"));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Pessoa pessoa(String nome, String cpf) {
        return Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome(nome).cpf(cpf).ativo(true).build();
    }

    private static Responsavel responsavelDe(Pessoa pessoa) {
        return Responsavel.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoa.getId()).build();
    }

    private static CadastroResponsavelRequest request(String cpf) {
        return new CadastroResponsavelRequest(" Maria Silva ", cpf, " maria@example.com ", "31999998888", null);
    }

    @Test
    void cadastrarCriaPessoaPapelEResponsavelNaEscolaDoToken() {
        when(responsavelRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of());
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(responsavelRepository.save(any(Responsavel.class))).thenAnswer(inv -> {
            Responsavel responsavel = inv.getArgument(0);
            responsavel.setId(UUID.randomUUID());
            return responsavel;
        });

        CadastroResponsavelResponse resposta = service.cadastrar(request(CPF));

        assertThat(resposta.escolaId()).isEqualTo(ESCOLA_A);
        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(resposta.email()).isEqualTo("maria@example.com");
        assertThat(resposta.cpf()).isEqualTo(CPF);
        assertThat(resposta.alunos()).isEmpty();

        ArgumentCaptor<PessoaPapel> papel = ArgumentCaptor.forClass(PessoaPapel.class);
        verify(pessoaPapelRepository).save(papel.capture());
        assertThat(papel.getValue().getPapel()).isEqualTo(PapelPessoa.RESPONSAVEL);
        assertThat(papel.getValue().getPessoaId()).isEqualTo(resposta.pessoaId());
    }

    @Test
    void cadastrarComCpfDeOutroResponsavelDaEscolaRecusa() {
        Pessoa existente = pessoa("Joana", CPF);
        when(responsavelRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(responsavelDe(existente)));
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A)))
                .thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.cadastrar(request(CPF))).isInstanceOf(CpfJaCadastradoException.class);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void atualizarMantendoOProprioCpfNaoAcusaRepeticao() {
        Pessoa pessoa = pessoa("Maria", CPF);
        Responsavel responsavel = responsavelDe(pessoa);
        when(responsavelRepository.findByIdAndEscolaId(responsavel.getId(), ESCOLA_A))
                .thenReturn(Optional.of(responsavel));
        when(pessoaRepository.findByIdAndEscolaId(pessoa.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoa));
        when(responsavelRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(responsavel));
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A))).thenReturn(List.of(pessoa));
        when(responsavelRepository.save(any(Responsavel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> inv.getArgument(0));

        CadastroResponsavelResponse resposta = service.atualizar(responsavel.getId(), request(CPF));

        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(responsavel.getAtualizadoEm()).isNotNull();
    }

    @Test
    void responsavelDeOutraEscolaResponde404() {
        UUID id = UUID.randomUUID();
        when(responsavelRepository.findByIdAndEscolaId(id, ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void vincularComoFinanceiroTiraAMarcaDoAnteriorAntesDeGravar() {
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(UUID.randomUUID())
                .matricula("2026-001").build();
        Pessoa pai = pessoa("Joao", CPF);
        Responsavel responsavel = responsavelDe(pai);
        AlunoResponsavel anterior = AlunoResponsavel.builder()
                .id(new AlunoResponsavelId(aluno.getId(), UUID.randomUUID())).parentesco("MAE").financeiro(true)
                .build();
        when(alunoRepository.findByIdAndEscolaId(aluno.getId(), ESCOLA_A)).thenReturn(Optional.of(aluno));
        when(responsavelRepository.findByIdAndEscolaId(responsavel.getId(), ESCOLA_A))
                .thenReturn(Optional.of(responsavel));
        when(pessoaRepository.findByIdAndEscolaId(pai.getId(), ESCOLA_A)).thenReturn(Optional.of(pai));
        when(alunoResponsavelRepository.findById_AlunoIdAndFinanceiroTrue(aluno.getId()))
                .thenReturn(Optional.of(anterior));
        when(alunoResponsavelRepository.findById(new AlunoResponsavelId(aluno.getId(), pai.getId())))
                .thenReturn(Optional.empty());

        ResponsavelResponse resposta = service.vincular(aluno.getId(), responsavel.getId(),
                new VinculoResponsavelRequest("PAI", true));

        assertThat(resposta.financeiro()).isTrue();
        assertThat(resposta.responsavelId()).isEqualTo(responsavel.getId());
        assertThat(resposta.pessoaId()).isEqualTo(pai.getId());
        assertThat(anterior.isFinanceiro()).isFalse();

        InOrder ordem = inOrder(alunoResponsavelRepository);
        ordem.verify(alunoResponsavelRepository).saveAndFlush(anterior);
        ArgumentCaptor<AlunoResponsavel> novo = ArgumentCaptor.forClass(AlunoResponsavel.class);
        ordem.verify(alunoResponsavelRepository).save(novo.capture());
        assertThat(novo.getValue().isFinanceiro()).isTrue();
        assertThat(novo.getValue().getParentesco()).isEqualTo("PAI");
    }

    @Test
    void vincularComoFinanceiroSemCpfValidoRecusaSemTirarOAnterior() {
        Aluno aluno = alunoDaEscola();
        Pessoa pai = pessoa("Joao", null);
        Responsavel responsavel = responsavelDe(pai);
        when(responsavelRepository.findByIdAndEscolaId(responsavel.getId(), ESCOLA_A))
                .thenReturn(Optional.of(responsavel));
        when(pessoaRepository.findByIdAndEscolaId(pai.getId(), ESCOLA_A)).thenReturn(Optional.of(pai));

        assertThatThrownBy(() -> service.vincular(aluno.getId(), responsavel.getId(),
                new VinculoResponsavelRequest("PAI", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF");
        verify(alunoResponsavelRepository, never()).findById_AlunoIdAndFinanceiroTrue(any());
        verify(alunoResponsavelRepository, never()).save(any());
    }

    @Test
    void vincularSemSerFinanceiroNaoExigeCpf() {
        Aluno aluno = alunoDaEscola();
        Pessoa avo = pessoa("Lucia", null);
        Responsavel responsavel = responsavelDe(avo);
        when(responsavelRepository.findByIdAndEscolaId(responsavel.getId(), ESCOLA_A))
                .thenReturn(Optional.of(responsavel));
        when(pessoaRepository.findByIdAndEscolaId(avo.getId(), ESCOLA_A)).thenReturn(Optional.of(avo));
        when(alunoResponsavelRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(service.vincular(aluno.getId(), responsavel.getId(), new VinculoResponsavelRequest("AVO", false))
                .financeiro()).isFalse();
    }

    private Aluno alunoDaEscola() {
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(UUID.randomUUID())
                .matricula("2026-001").build();
        when(alunoRepository.findByIdAndEscolaId(aluno.getId(), ESCOLA_A)).thenReturn(Optional.of(aluno));
        return aluno;
    }

    /** As gravacoes devolvem o que receberam, com id. */
    private void criacaoLiberada() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
        when(responsavelRepository.save(any(Responsavel.class))).thenAnswer(inv -> {
            Responsavel responsavel = inv.getArgument(0);
            responsavel.setId(UUID.randomUUID());
            return responsavel;
        });
    }

    @Test
    void incluirNoAlunoCriaOResponsavelEMarcaOFinanceiro() {
        Aluno aluno = alunoDaEscola();
        criacaoLiberada();
        AlunoResponsavel anterior = AlunoResponsavel.builder()
                .id(new AlunoResponsavelId(aluno.getId(), UUID.randomUUID())).financeiro(true).build();
        when(alunoResponsavelRepository.findById_AlunoIdAndFinanceiroTrue(aluno.getId()))
                .thenReturn(Optional.of(anterior));

        ResponsavelResponse resposta = service.incluirNoAluno(aluno.getId(),
                new ResponsavelRequest(" Marta ", CPF, null, null, "MAE", true));

        assertThat(resposta.nome()).isEqualTo("Marta");
        assertThat(resposta.financeiro()).isTrue();
        assertThat(anterior.isFinanceiro()).isFalse();
        verify(alunoResponsavelRepository).saveAndFlush(anterior);
    }

    @Test
    void incluirNoAlunoComCpfJaCadastradoReaproveitaOResponsavel() {
        Aluno aluno = alunoDaEscola();
        Pessoa mae = pessoa("Marta", CPF);
        Responsavel existente = responsavelDe(mae);
        when(responsavelRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(existente));
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(mae.getId()), ESCOLA_A)).thenReturn(List.of(mae));

        ResponsavelResponse resposta = service.incluirNoAluno(aluno.getId(),
                new ResponsavelRequest("Outro Nome", CPF, null, null, "MAE", false));

        assertThat(resposta.responsavelId()).isEqualTo(existente.getId());
        assertThat(resposta.nome()).isEqualTo("Marta");
        verify(responsavelRepository, never()).save(any());
    }

    @Test
    void incluirNoAlunoJaVinculadoRecusa() {
        Aluno aluno = alunoDaEscola();
        Pessoa mae = pessoa("Marta", CPF);
        when(responsavelRepository.findAllByEscolaId(ESCOLA_A)).thenReturn(List.of(responsavelDe(mae)));
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(mae.getId()), ESCOLA_A)).thenReturn(List.of(mae));
        when(alunoResponsavelRepository.existsById(new AlunoResponsavelId(aluno.getId(), mae.getId())))
                .thenReturn(true);

        assertThatThrownBy(() -> service.incluirNoAluno(aluno.getId(),
                new ResponsavelRequest("Marta", CPF, null, null, "MAE", true)))
                .isInstanceOf(RegistroEmUsoException.class);
        verify(alunoResponsavelRepository, never()).save(any());
    }

    @Test
    void incluirNoAlunoRecusaFinanceiroSemCpfECpfInvalido() {
        Aluno aluno = alunoDaEscola();
        criacaoLiberada();

        assertThatThrownBy(() -> service.incluirNoAluno(aluno.getId(),
                new ResponsavelRequest("Marta", null, null, null, "MAE", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("financeiro");
        assertThatThrownBy(() -> service.incluirNoAluno(aluno.getId(),
                new ResponsavelRequest("Marta", "11111111111", null, null, "MAE", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalido");
        verify(alunoResponsavelRepository, never()).save(any());
    }

    @Test
    void desvincularComCobrancaEmAbertoRecusa() {
        Aluno aluno = Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(UUID.randomUUID())
                .matricula("2026-001").build();
        Pessoa mae = pessoa("Maria", CPF);
        Responsavel responsavel = responsavelDe(mae);
        AlunoResponsavelId id = new AlunoResponsavelId(aluno.getId(), mae.getId());
        when(alunoRepository.findByIdAndEscolaId(aluno.getId(), ESCOLA_A)).thenReturn(Optional.of(aluno));
        when(responsavelRepository.findByIdAndEscolaId(responsavel.getId(), ESCOLA_A))
                .thenReturn(Optional.of(responsavel));
        when(alunoResponsavelRepository.findById(id))
                .thenReturn(Optional.of(AlunoResponsavel.builder().id(id).financeiro(true).build()));
        when(cobrancaRepository.existsByEscolaIdAndAluno_IdAndResponsavelIdAndStatusIn(eq(ESCOLA_A),
                eq(aluno.getId()), eq(mae.getId()), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.desvincular(aluno.getId(), responsavel.getId()))
                .isInstanceOf(RegistroEmUsoException.class);
        verify(alunoResponsavelRepository, never()).delete(any());
    }

    @Test
    void excluirResponsavelAindaVinculadoRecusa() {
        Pessoa mae = pessoa("Maria", CPF);
        Responsavel responsavel = responsavelDe(mae);
        when(responsavelRepository.findByIdAndEscolaId(responsavel.getId(), ESCOLA_A))
                .thenReturn(Optional.of(responsavel));
        when(pessoaRepository.findByIdAndEscolaId(mae.getId(), ESCOLA_A)).thenReturn(Optional.of(mae));
        when(alunoResponsavelRepository.existsById_PessoaId(mae.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(responsavel.getId())).isInstanceOf(RegistroEmUsoException.class);
        verify(responsavelRepository, never()).delete(any());
    }

    @Test
    void excluirSemVinculoNemCobrancaApagaResponsavelPapelEPessoa() {
        Pessoa mae = pessoa("Maria", CPF);
        Responsavel responsavel = responsavelDe(mae);
        PessoaPapel papel = PessoaPapel.builder().id(UUID.randomUUID()).pessoaId(mae.getId())
                .papel(PapelPessoa.RESPONSAVEL).build();
        when(responsavelRepository.findByIdAndEscolaId(responsavel.getId(), ESCOLA_A))
                .thenReturn(Optional.of(responsavel));
        when(pessoaRepository.findByIdAndEscolaId(mae.getId(), ESCOLA_A)).thenReturn(Optional.of(mae));
        when(pessoaPapelRepository.findAllByPessoaId(mae.getId())).thenReturn(List.of(papel));

        service.excluir(responsavel.getId());

        verify(responsavelRepository).delete(responsavel);
        verify(pessoaPapelRepository).deleteAll(List.of(papel));
        verify(pessoaRepository).delete(mae);
    }
}
