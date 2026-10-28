package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;

/** Spec §4.3: nenhuma transacao de banco aberta durante chamada ao provedor. */
class ChamadorProvedorTest {

    private static final CredencialProvedor CREDENCIAL = new CredencialProvedor("chave");

    private PaymentProvider provider;
    private ChamadorProvedor chamador;

    @BeforeEach
    void setUp() {
        provider = mock(PaymentProvider.class);
        chamador = new ChamadorProvedor(provider);
    }

    @AfterEach
    void limparTransacao() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void semTransacaoChamaOProvedor() {
        SituacaoContaProvedor situacao = new SituacaoContaProvedor(null, null, null, null);
        when(provider.consultarSituacaoConta(CREDENCIAL)).thenReturn(situacao);

        SituacaoContaProvedor devolvida = chamador.chamar(p -> p.consultarSituacaoConta(CREDENCIAL));

        assertThat(devolvida).isSameAs(situacao);
        chamador.executar(p -> p.removerCobranca(CREDENCIAL, "pay_1"));

        verify(provider).removerCobranca(CREDENCIAL, "pay_1");
    }

    @Test
    void comTransacaoAbertaRecusaSemChamarOProvedor() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> chamador.chamar(p -> p.consultarSituacaoConta(CREDENCIAL)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> chamador.executar(p -> p.removerCobranca(CREDENCIAL, "pay_1")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(provider);
    }
}
