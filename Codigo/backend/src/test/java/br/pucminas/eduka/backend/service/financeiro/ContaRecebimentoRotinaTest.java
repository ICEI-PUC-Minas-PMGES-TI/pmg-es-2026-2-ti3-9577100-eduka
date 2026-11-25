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
 * Rotina que acompanha a aprovacao das contas de recebimento: o que ela roda
 * e o que acontece se falhar. A leitura de cada conta no provedor fica no
 * roteador; o intervalo, no AgendaDasRotinasTest.
 */
@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class ContaRecebimentoRotinaTest {

    @Mock
    private PaymentRoutingService paymentRoutingService;

    @Test
    void sincronizaAsContasAguardandoERegistraQuantas(CapturedOutput saida) {
        when(paymentRoutingService.sincronizarContasAguardando()).thenReturn(2);

        new ContaRecebimentoRotina(paymentRoutingService).sincronizar();

        verify(paymentRoutingService).sincronizarContasAguardando();
        assertThat(saida).contains("Rotina de contas: 2 conta(s) de recebimento sincronizada(s)");
    }

    @Test
    void semContaAguardandoNaoEscreveNoLog(CapturedOutput saida) {
        new ContaRecebimentoRotina(paymentRoutingService).sincronizar();

        verify(paymentRoutingService).sincronizarContasAguardando();
        assertThat(saida).doesNotContain("Rotina de contas");
    }

    @Test
    void falhaGeralSobeParaOAgendador() {
        when(paymentRoutingService.sincronizarContasAguardando())
                .thenThrow(new DataAccessResourceFailureException("banco fora do ar"));

        // O agendador do Spring registra o erro; a conta segue AGUARDANDO_APROVACAO e volta na proxima rodada.
        assertThatThrownBy(() -> new ContaRecebimentoRotina(paymentRoutingService).sincronizar())
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
