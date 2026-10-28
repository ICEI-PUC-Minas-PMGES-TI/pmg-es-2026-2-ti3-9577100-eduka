package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;

/**
 * Quem recebe o aviso: quem paga pela regra R18 do provedor (responsavel da
 * cobranca, senao o responsavel financeiro do aluno), com e-mail; senao o
 * aluno; senao ninguem.
 */
@ExtendWith(MockitoExtension.class)
class AvisoCobrancaServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();

    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private EscolaRepository escolaRepository;
    @Mock
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @Mock
    private ApplicationEventPublisher eventos;
    @Mock
    private Escola escola;

    private AvisoCobrancaService service;

    @BeforeEach
    void setUp() {
        service = new AvisoCobrancaService(pessoaRepository, escolaRepository, alunoResponsavelRepository, eventos);
    }

    private void comEscola() {
        when(escola.getId()).thenReturn(ESCOLA_A);
        when(escola.getNome()).thenReturn("Colegio Horizonte");
        when(escolaRepository.findAllById(List.of(ESCOLA_A))).thenReturn(List.of(escola));
    }

    private static Pessoa pessoa(String nome, String email) {
        return Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome(nome).email(email).ativo(true).build();
    }

    private static CobrancaParaAviso cobranca(Pessoa aluno, Pessoa responsavel) {
        return new CobrancaParaAviso(UUID.randomUUID(), ESCOLA_A, aluno.getId(),
                responsavel == null ? null : responsavel.getId(), "Mensalidade", new BigDecimal("850.50"),
                LocalDate.of(2026, 11, 10), null);
    }

    private AvisoCobranca publicado() {
        ArgumentCaptor<AvisoCobranca> aviso = ArgumentCaptor.forClass(AvisoCobranca.class);
        verify(eventos).publishEvent(aviso.capture());
        return aviso.getValue();
    }

    @Test
    void vaiParaOResponsavelFinanceiroQuandoEleTemEmail() {
        comEscola();
        Pessoa aluno = pessoa("Ana", "ana@escola.com");
        Pessoa mae = pessoa("Marta", " marta@exemplo.com ");
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A))).thenReturn(List.of(aluno, mae));

        assertThat(service.publicar(AvisoCobranca.Tipo.COBRANCA_CRIADA, List.of(cobranca(aluno, mae)))).isEqualTo(1);

        AvisoCobranca aviso = publicado();
        assertThat(aviso.destinatarioEmail()).isEqualTo("marta@exemplo.com");
        assertThat(aviso.destinatarioNome()).isEqualTo("Marta");
        assertThat(aviso.destinatarioEhAluno()).isFalse();
        assertThat(aviso.alunoNome()).isEqualTo("Ana");
        assertThat(aviso.escolaNome()).isEqualTo("Colegio Horizonte");
        assertThat(aviso.tipo()).isEqualTo(AvisoCobranca.Tipo.COBRANCA_CRIADA);
    }

    @Test
    void responsavelSemEmailOuSemResponsavelVaiParaOAluno() {
        comEscola();
        Pessoa aluno = pessoa("Ana", "ana@escola.com");
        Pessoa pai = pessoa("Joao", null);
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A))).thenReturn(List.of(aluno, pai));

        service.publicar(AvisoCobranca.Tipo.VENCIMENTO_PROXIMO, cobranca(aluno, pai));

        AvisoCobranca aviso = publicado();
        assertThat(aviso.destinatarioEmail()).isEqualTo("ana@escola.com");
        assertThat(aviso.destinatarioEhAluno()).isTrue();
    }

    @Test
    void ninguemComEmailNaoPublica() {
        comEscola();
        Pessoa aluno = pessoa("Ana", " ");
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A))).thenReturn(List.of(aluno));

        assertThat(service.publicar(AvisoCobranca.Tipo.PAGAMENTO_CONFIRMADO, List.of(cobranca(aluno, null))))
                .isZero();
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void toStringNaoExpoeDadoPessoal() {
        AvisoCobranca aviso = new AvisoCobranca(AvisoCobranca.Tipo.COBRANCA_CRIADA, UUID.randomUUID(),
                "marta@exemplo.com", "Marta", false, "Ana", "Escola", "Mensalidade", BigDecimal.TEN,
                LocalDate.of(2026, 11, 10), null);
        assertThat(aviso.toString()).doesNotContain("marta", "Marta", "Ana");
    }

    // ---- R18: o responsavel financeiro do aluno quando a cobranca nao tem responsavel ----

    @Test
    void semResponsavelNaCobrancaVaiParaOResponsavelFinanceiroDoAluno() {
        comEscola();
        Pessoa aluno = pessoa("Ana", "ana@escola.com");
        Pessoa mae = pessoa("Marta", "marta@exemplo.com");
        when(alunoResponsavelRepository.financeiroPorPessoaDoAluno(List.of(aluno.getId())))
                .thenReturn(Map.of(aluno.getId(), mae.getId()));
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A))).thenReturn(List.of(aluno, mae));

        service.publicar(AvisoCobranca.Tipo.VENCIMENTO_PROXIMO, cobranca(aluno, null));

        AvisoCobranca aviso = publicado();
        assertThat(aviso.destinatarioEmail()).isEqualTo("marta@exemplo.com");
        assertThat(aviso.destinatarioEhAluno()).isFalse();
        assertThat(aviso.alunoNome()).isEqualTo("Ana");
    }

    @Test
    void responsavelDaCobrancaTemPrecedenciaSobreOFinanceiroDoAluno() {
        comEscola();
        Pessoa aluno = pessoa("Ana", "ana@escola.com");
        Pessoa avo = pessoa("Lucia", "lucia@exemplo.com");
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A))).thenReturn(List.of(aluno, avo));

        service.publicar(AvisoCobranca.Tipo.COBRANCA_CRIADA, cobranca(aluno, avo));

        assertThat(publicado().destinatarioEmail()).isEqualTo("lucia@exemplo.com");
        // A cobranca ja diz quem paga: nem procura o financeiro.
        verify(alunoResponsavelRepository).financeiroPorPessoaDoAluno(List.of());
    }

    @Test
    void financeiroSemEmailVaiParaOAluno() {
        comEscola();
        Pessoa aluno = pessoa("Ana", "ana@escola.com");
        Pessoa pai = pessoa("Joao", null);
        when(alunoResponsavelRepository.financeiroPorPessoaDoAluno(List.of(aluno.getId())))
                .thenReturn(Map.of(aluno.getId(), pai.getId()));
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A))).thenReturn(List.of(aluno, pai));

        service.publicar(AvisoCobranca.Tipo.COBRANCA_CRIADA, cobranca(aluno, null));

        AvisoCobranca aviso = publicado();
        assertThat(aviso.destinatarioEmail()).isEqualTo("ana@escola.com");
        assertThat(aviso.destinatarioEhAluno()).isTrue();
    }

    @Test
    void financeiroDeOutraEscolaNaoRecebe() {
        comEscola();
        Pessoa aluno = pessoa("Ana", "ana@escola.com");
        UUID pessoaDeOutraEscola = UUID.randomUUID();
        when(alunoResponsavelRepository.financeiroPorPessoaDoAluno(List.of(aluno.getId())))
                .thenReturn(Map.of(aluno.getId(), pessoaDeOutraEscola));
        // A busca por escola nao devolve a pessoa de outra escola.
        when(pessoaRepository.findAllByIdInAndEscolaId(anyCollection(), eq(ESCOLA_A))).thenReturn(List.of(aluno));

        service.publicar(AvisoCobranca.Tipo.COBRANCA_CRIADA, cobranca(aluno, null));

        assertThat(publicado().destinatarioEmail()).isEqualTo("ana@escola.com");
    }
}
