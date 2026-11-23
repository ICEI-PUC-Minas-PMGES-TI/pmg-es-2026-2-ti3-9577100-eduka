package br.pucminas.eduka.backend.service.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;

/**
 * Rotina de reconciliacao: o que ela roda e o que acontece se falhar. A
 * consulta ao provedor e as regras ficam no roteador; o intervalo, no
 * AgendaDasRotinasTest.
 */
@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class ReconciliacaoRotinaTest {

    private static final int LOTE = 50;

    @Mock
    private PaymentRoutingService paymentRoutingService;

    @Test
    void reconciliaOLoteConfiguradoERegistraOQueResolveu(CapturedOutput saida) {
        when(paymentRoutingService.reconciliarEmissoesPendentes(LOTE)).thenReturn(3);

        new ReconciliacaoRotina(paymentRoutingService, LOTE).reconciliar();

        verify(paymentRoutingService).reconciliarEmissoesPendentes(LOTE);
        assertThat(saida).contains("Rotina de reconciliacao: 3 pendencia(s) resolvida(s)");
    }

    @Test
    void semPendenciaNaoEscreveNoLog(CapturedOutput saida) {
        // Roda a cada minuto: uma linha por rodada vazia so faria ruido.
        new ReconciliacaoRotina(paymentRoutingService, LOTE).reconciliar();

        verify(paymentRoutingService).reconciliarEmissoesPendentes(LOTE);
        assertThat(saida).doesNotContain("Rotina de reconciliacao");
    }

    @Test
    void falhaGeralSobeParaOAgendador() {
        when(paymentRoutingService.reconciliarEmissoesPendentes(LOTE))
                .thenThrow(new DataAccessResourceFailureException("banco fora do ar"));

        // O agendador do Spring registra o erro e tenta de novo na proxima rodada:
        // a emissao segue EMITINDO e volta a ser procurada.
        assertThatThrownBy(() -> new ReconciliacaoRotina(paymentRoutingService, LOTE).reconciliar())
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
