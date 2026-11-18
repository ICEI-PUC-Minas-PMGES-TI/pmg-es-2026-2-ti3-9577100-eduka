package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.pucminas.eduka.backend.domain.financeiro.NivelRiscoAtraso;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.financeiro.RiscoAtrasoResponse;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.service.financeiro.RiscoAtrasoService;

/**
 * A consulta do risco de atraso no Postgres: janela, canceladas, pagamento
 * online e baixa manual, nomes decifrados e isolamento entre escolas.
 */
class RiscoAtrasoBancoRealTest extends BancoRealTest {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal MENSALIDADE = new BigDecimal("400.00");

    @Autowired
    private RiscoAtrasoService riscoAtrasoService;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate hoje = LocalDate.now(FUSO_ESCOLA);

    /** Meio-dia do dia, no fuso da escola: o dia nao muda com a hora do teste. */
    private static Instant meioDia(LocalDate dia) {
        return dia.atTime(12, 0).atZone(FUSO_ESCOLA).toInstant();
    }

    @Test
    void listaDoMaiorParaOMenorRiscoSoDaEscola() {
        UUID escola = dados.escola();
        UUID ana = dados.aluno(escola, "Ana Souza");
        UUID bruno = dados.aluno(escola, "Bruno Lima");
        UUID carla = dados.aluno(escola, "Carla Dias");

        // Ana: quatro pagas com 20 dias de atraso (baixa manual) e duas vencidas em aberto: alto.
        for (int mes = 3; mes <= 6; mes++) {
            LocalDate vencimento = hoje.minusMonths(mes);
            dados.cobrancaPaga(escola, ana, MENSALIDADE, vencimento, meioDia(vencimento.plusDays(20)));
        }
        dados.cobranca(escola, ana, StatusCobranca.EM_ATRASO, hoje.minusMonths(2), MENSALIDADE);
        dados.cobranca(escola, ana, StatusCobranca.PENDENTE, hoje.minusMonths(1), MENSALIDADE);

        // Bruno: uma vencida ha 40 dias: medio.
        dados.cobranca(escola, bruno, StatusCobranca.EM_ATRASO, hoje.minusDays(40), MENSALIDADE);
        // Cancelada nao conta.
        dados.cobranca(escola, bruno, StatusCobranca.CANCELADA, hoje.minusDays(90), MENSALIDADE);

        // Carla: paga online hoje com vencimento hoje: em dia.
        UUID emDia = dados.cobrancaPaga(escola, carla, MENSALIDADE, hoje, meioDia(hoje));
        dados.pagamentoOnline(escola, emDia, MENSALIDADE, FormaPagamento.PIX);

        // Outra escola com aluno em atraso: nao aparece.
        UUID outra = dados.escola();
        dados.cobranca(outra, dados.aluno(outra, "Davi Rocha"), StatusCobranca.EM_ATRASO, hoje.minusDays(60),
                MENSALIDADE);

        autenticarComo("FINANCEIRO", escola);
        List<RiscoAtrasoResponse> riscos = riscoAtrasoService.daEscola(null);

        assertThat(riscos).extracting(RiscoAtrasoResponse::alunoNome)
                .containsExactly("Ana Souza", "Bruno Lima", "Carla Dias");
        assertThat(riscos).extracting(RiscoAtrasoResponse::nivel)
                .containsExactly(NivelRiscoAtraso.ALTO, NivelRiscoAtraso.MEDIO, NivelRiscoAtraso.BAIXO);

        RiscoAtrasoResponse riscoAna = riscos.getFirst();
        assertThat(riscoAna.indicadores().atrasosPagos()).isEqualTo(4);
        assertThat(riscoAna.indicadores().atrasosEmAberto()).isEqualTo(2);
        assertThat(riscoAna.indicadores().valorEmAtraso()).isEqualByComparingTo("800.00");

        RiscoAtrasoResponse riscoBruno = riscos.get(1);
        assertThat(riscoBruno.indicadores().cobrancasVencidas()).isEqualTo(1);
        assertThat(riscoBruno.indicadores().diasMediosAtraso()).isEqualTo(40);

        assertThat(riscoAtrasoService.daEscola(NivelRiscoAtraso.MEDIO)).extracting(RiscoAtrasoResponse::alunoNome)
                .containsExactly("Ana Souza", "Bruno Lima");
    }

    @Test
    void pagamentoOnlineValeADataDoProvedor() {
        UUID escola = dados.escola();
        UUID ana = dados.aluno(escola, "Ana Souza");
        // PAGA_EM (o aviso chegou) 30 dias depois do vencimento; o provedor diz que o boleto foi pago em 10.
        LocalDate vencimento = hoje.minusDays(40);
        UUID paga = dados.cobrancaPaga(escola, ana, MENSALIDADE, vencimento, meioDia(vencimento.plusDays(30)));
        dados.pagamentoOnline(escola, paga, MENSALIDADE, FormaPagamento.BOLETO);
        jdbc.update("UPDATE PAGAMENTO_COBRANCA SET DATA_PAGAMENTO = ? WHERE COBRANCA_ID = ?",
                vencimento.plusDays(10), paga);

        autenticarComo("MASTER", escola);
        RiscoAtrasoResponse risco = riscoAtrasoService.doAluno(ana);

        assertThat(risco.alunoNome()).isEqualTo("Ana Souza");
        assertThat(risco.indicadores().atrasosPagos()).isEqualTo(1);
        assertThat(risco.indicadores().diasMediosAtraso()).isEqualTo(10);
    }
}
