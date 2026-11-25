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
 * Rotina que reaplica os avisos do provedor: o que ela roda e o que acontece
 * se falhar. A aplicacao de cada aviso e o limite de tentativas ficam no
 * roteador; o intervalo, no AgendaDasRotinasTest.
 */
@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class EventoProvedorRotinaTest {

    private static final int MAX_TENTATIVAS = 10;
    private static final int LOTE = 50;

    @Mock
    private PaymentRoutingService paymentRoutingService;

    private EventoProvedorRotina rotina() {
        return new EventoProvedorRotina(paymentRoutingService, MAX_TENTATIVAS, LOTE);
    }

    @Test
    void reaplicaComOLimiteDeTentativasEOLoteConfigurados(CapturedOutput saida) {
        when(paymentRoutingService.processarEventosPendentes(MAX_TENTATIVAS, LOTE)).thenReturn(4);

        rotina().reprocessar();

        verify(paymentRoutingService).processarEventosPendentes(MAX_TENTATIVAS, LOTE);
        assertThat(saida).contains("Rotina de avisos: 4 aviso(s) do provedor reaplicado(s)");
    }

    @Test
    void semAvisoPendenteNaoEscreveNoLog(CapturedOutput saida) {
        rotina().reprocessar();

        verify(paymentRoutingService).processarEventosPendentes(MAX_TENTATIVAS, LOTE);
        assertThat(saida).doesNotContain("Rotina de avisos");
    }

    @Test
    void falhaGeralSobeParaOAgendador() {
        when(paymentRoutingService.processarEventosPendentes(MAX_TENTATIVAS, LOTE))
                .thenThrow(new DataAccessResourceFailureException("banco fora do ar"));

        // O agendador do Spring registra o erro; o aviso segue pendente e volta na proxima rodada.
        assertThatThrownBy(() -> rotina().reprocessar()).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
