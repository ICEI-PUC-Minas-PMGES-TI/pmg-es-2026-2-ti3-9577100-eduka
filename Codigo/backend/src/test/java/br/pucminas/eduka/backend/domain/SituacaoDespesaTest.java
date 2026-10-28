package br.pucminas.eduka.backend.domain;

import static br.pucminas.eduka.backend.domain.SituacaoDespesa.A_PAGAR;
import static br.pucminas.eduka.backend.domain.SituacaoDespesa.CANCELADA;
import static br.pucminas.eduka.backend.domain.SituacaoDespesa.PAGA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SituacaoDespesaTest {

    @Test
    void aPagarPodeSerPagaOuCancelada() {
        assertThat(A_PAGAR.podeMudarPara(PAGA)).isTrue();
        assertThat(A_PAGAR.podeMudarPara(CANCELADA)).isTrue();
    }

    @Test
    void pagaSoVoltaParaAPagar() {
        assertThat(PAGA.podeMudarPara(A_PAGAR)).isTrue();
        assertThat(PAGA.podeMudarPara(CANCELADA)).isFalse();
    }

    @Test
    void canceladaEFinal() {
        for (SituacaoDespesa nova : SituacaoDespesa.values()) {
            assertThat(CANCELADA.podeMudarPara(nova)).isFalse();
        }
    }

    @Test
    void ficarNaMesmaSituacaoNaoEMudanca() {
        for (SituacaoDespesa situacao : SituacaoDespesa.values()) {
            assertThat(situacao.podeMudarPara(situacao)).isFalse();
        }
    }
}
