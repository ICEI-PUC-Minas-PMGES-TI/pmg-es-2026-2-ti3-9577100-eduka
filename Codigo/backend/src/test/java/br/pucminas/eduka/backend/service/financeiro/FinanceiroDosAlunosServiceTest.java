package br.pucminas.eduka.backend.service.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.dto.financeiro.FiltroFinanceiroAlunos;
import br.pucminas.eduka.backend.dto.financeiro.FinanceiroDoAlunoResponse;
import br.pucminas.eduka.backend.dto.financeiro.FinanceiroDoAlunoResponse.FaixaAtraso;
import br.pucminas.eduka.backend.dto.financeiro.FinanceiroDoAlunoResponse.Situacao;
import br.pucminas.eduka.backend.dto.financeiro.ResumoFinanceiroAlunosResponse;
import br.pucminas.eduka.backend.exception.escola.ModuloNaoContratadoException;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository.FinanceiroPorAluno;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PerfilNaoPermitidoException;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Situacao financeira dos alunos (lista de alunos, IU07): quem ve, o "hoje"
 * no fuso da escola e como os totais de cada aluno viram situacao, faixa de
 * atraso e os filtros.
 */
@ExtendWith(MockitoExtension.class)
class FinanceiroDosAlunosServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    /** 22h de 30/11/2026 em Brasilia: em UTC ja e 01/12. */
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 30);

    @Mock
    private CobrancaRepository cobrancaRepository;
    @Mock
    private EscolaRepository escolaRepository;

    private FinanceiroDosAlunosService service;

    @BeforeEach
    void setUp() {
        service = new FinanceiroDosAlunosService(cobrancaRepository, escolaRepository, new IsolamentoEscola(),
                RELOGIO);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarComo(String perfil) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), perfil, ESCOLA);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(usuario, null, "ROLE_" + perfil));
    }

    private void comGestaoFinanceira(boolean contratada) {
        when(escolaRepository.modulos(ESCOLA))
                .thenReturn(contratada ? List.of("GESTAO_ACADEMICA", "GESTAO_FINANCEIRA") : List.of("GESTAO_ACADEMICA"));
    }

    private record Totais(UUID getAlunoId, long getEmAtraso, long getPendentesNoPrazo, BigDecimal getValorEmAberto,
            BigDecimal getValorEmAtraso, LocalDate getVencimentoMaisAntigoEmAtraso, LocalDate getProximoVencimento,
            BigDecimal getValorVenceAte, long getMensalidadesNoMes) implements FinanceiroPorAluno {
    }

    // ---- Quem ve ----

    @Test
    void masterEFinanceiroVeemComAGestaoFinanceira() {
        comGestaoFinanceira(true);
        autenticarComo("MASTER");
        assertThat(service.disponivel()).isTrue();
        autenticarComo("FINANCEIRO");
        assertThat(service.disponivel()).isTrue();
    }

    @Test
    void secretariaNaoVeNemConsultaOsModulos() {
        autenticarComo("SECRETARIA");

        assertThat(service.disponivel()).isFalse();
        assertThatThrownBy(service::exigirDisponivel).isInstanceOf(PerfilNaoPermitidoException.class);
        verify(escolaRepository, never()).modulos(any());
    }

    @Test
    void masterSemAGestaoFinanceiraNaoVe() {
        comGestaoFinanceira(false);
        autenticarComo("MASTER");

        assertThat(service.disponivel()).isFalse();
        assertThatThrownBy(service::exigirDisponivel).isInstanceOf(ModuloNaoContratadoException.class);
    }

    // ---- Situacao de cada aluno ----

    @Test
    void consultaComOHojeEOMesDaEscolaENaoDoUtc() {
        autenticarComo("FINANCEIRO");
        when(cobrancaRepository.financeiroPorAluno(ESCOLA, HOJE, HOJE.plusDays(7), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30))).thenReturn(List.of());

        assertThat(service.porAluno()).isEmpty();
    }

    @Test
    void atrasoValeMaisQueAVencerEContaOsDiasDaCobrancaMaisAntiga() {
        autenticarComo("FINANCEIRO");
        UUID devendo = UUID.randomUUID();
        UUID aVencer = UUID.randomUUID();
        UUID emDia = UUID.randomUUID();
        when(cobrancaRepository.financeiroPorAluno(any(), any(), any(), any(), any())).thenReturn(List.of(
                new Totais(devendo, 2, 1, new BigDecimal("450.00"), new BigDecimal("300.00"), HOJE.minusDays(45),
                        HOJE.plusDays(3), new BigDecimal("150.00"), 1),
                new Totais(aVencer, 0, 1, new BigDecimal("150.00"), null, null, HOJE.plusDays(10), null, 0),
                new Totais(emDia, 0, 0, null, null, null, null, null, 1)));

        Map<UUID, FinanceiroDoAlunoResponse> porAluno = service.porAluno();

        assertThat(porAluno.get(devendo)).isEqualTo(new FinanceiroDoAlunoResponse(Situacao.EM_ATRASO,
                new BigDecimal("450.00"), new BigDecimal("300.00"), 45, FaixaAtraso.DE_31_A_60_DIAS, HOJE.plusDays(3),
                true, new BigDecimal("150.00"), true));
        assertThat(porAluno.get(aVencer)).isEqualTo(new FinanceiroDoAlunoResponse(Situacao.A_VENCER,
                new BigDecimal("150.00"), BigDecimal.ZERO, null, null, HOJE.plusDays(10), false, BigDecimal.ZERO,
                false));
        assertThat(porAluno.get(emDia).situacao()).isEqualTo(Situacao.EM_DIA);
        assertThat(porAluno.get(emDia).valorEmAberto()).isZero();
    }

    @Test
    void venceEm7DiasIncluiOSetimoDiaENaoOOitavo() {
        assertThat(FinanceiroDoAlunoResponse.de(0, 1, BigDecimal.TEN, null, null, HOJE.plusDays(7), BigDecimal.TEN,
                false, HOJE).venceEm7Dias()).isTrue();
        assertThat(FinanceiroDoAlunoResponse.de(0, 1, BigDecimal.TEN, null, null, HOJE.plusDays(8), null,
                false, HOJE).venceEm7Dias()).isFalse();
    }

    @Test
    void faixasDeAtrasoNosLimites() {
        assertThat(FaixaAtraso.de(1)).isEqualTo(FaixaAtraso.ATE_30_DIAS);
        assertThat(FaixaAtraso.de(30)).isEqualTo(FaixaAtraso.ATE_30_DIAS);
        assertThat(FaixaAtraso.de(31)).isEqualTo(FaixaAtraso.DE_31_A_60_DIAS);
        assertThat(FaixaAtraso.de(60)).isEqualTo(FaixaAtraso.DE_31_A_60_DIAS);
        assertThat(FaixaAtraso.de(61)).isEqualTo(FaixaAtraso.MAIS_DE_60_DIAS);
    }

    // ---- Filtros e resumo ----

    @Test
    void filtrosCombinamComE() {
        FinanceiroDoAlunoResponse atrasadoHa10Dias = FinanceiroDoAlunoResponse.de(1, 0, BigDecimal.TEN,
                BigDecimal.TEN, HOJE.minusDays(10), null, null, false, HOJE);

        assertThat(FiltroFinanceiroAlunos.NENHUM.vazio()).isTrue();
        assertThat(new FiltroFinanceiroAlunos(Situacao.EM_ATRASO, FaixaAtraso.ATE_30_DIAS, false, true)
                .aceita(atrasadoHa10Dias)).isTrue();
        assertThat(new FiltroFinanceiroAlunos(Situacao.EM_ATRASO, FaixaAtraso.MAIS_DE_60_DIAS, false, false)
                .aceita(atrasadoHa10Dias)).isFalse();
        assertThat(new FiltroFinanceiroAlunos(null, null, true, false).aceita(atrasadoHa10Dias)).isFalse();
        assertThat(new FiltroFinanceiroAlunos(Situacao.SEM_COBRANCA, null, false, false)
                .aceita(FinanceiroDoAlunoResponse.SEM_COBRANCA)).isTrue();
    }

    @Test
    void resumoContaTodasAsSituacoesEFaixasComZero() {
        FinanceiroDoAlunoResponse atrasado = FinanceiroDoAlunoResponse.de(1, 1, new BigDecimal("250.00"),
                new BigDecimal("100.00"), HOJE.minusDays(70), HOJE.plusDays(2), new BigDecimal("150.00"), true, HOJE);
        FinanceiroDoAlunoResponse aVencer = FinanceiroDoAlunoResponse.de(0, 1, new BigDecimal("80.00"), null, null,
                HOJE, new BigDecimal("80.00"), true, HOJE);

        ResumoFinanceiroAlunosResponse resumo = ResumoFinanceiroAlunosResponse
                .de(List.of(atrasado, aVencer, FinanceiroDoAlunoResponse.SEM_COBRANCA));

        assertThat(resumo.porSituacao()).containsExactlyInAnyOrderEntriesOf(Map.of(Situacao.EM_ATRASO, 1L,
                Situacao.A_VENCER, 1L, Situacao.EM_DIA, 0L, Situacao.SEM_COBRANCA, 1L));
        assertThat(resumo.porFaixaAtraso()).containsExactlyInAnyOrderEntriesOf(Map.of(FaixaAtraso.ATE_30_DIAS, 0L,
                FaixaAtraso.DE_31_A_60_DIAS, 0L, FaixaAtraso.MAIS_DE_60_DIAS, 1L));
        assertThat(resumo.valorEmAtraso()).isEqualByComparingTo("100.00");
        assertThat(resumo.venceEm7Dias()).isEqualTo(2);
        assertThat(resumo.valorVenceEm7Dias()).isEqualByComparingTo("230.00");
        assertThat(resumo.semMensalidadeNoMes()).isEqualTo(1);
    }
}
