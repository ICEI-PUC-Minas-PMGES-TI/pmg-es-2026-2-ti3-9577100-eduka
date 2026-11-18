package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;

/** O que o {@code CobrancaService} pergunta ao roteador antes de editar e cancelar (R24, spec §8.5). */
class PaymentRoutingServiceCobrancaTest {

    private RoteadorPagamento roteadorPagamento;
    private PaymentRoutingService roteador;

    @BeforeEach
    void setUp() {
        roteadorPagamento = mock(RoteadorPagamento.class);
        roteador = new PaymentRoutingService(new ChamadorProvedor(mock(PaymentProvider.class)),
                mock(RoteadorConta.class), roteadorPagamento, mock(RoteadorEventos.class), mock(RoteadorFatura.class),
                mock(RoteadorSandbox.class), mock(RoteadorReconciliacao.class));
    }

    @ParameterizedTest
    @EnumSource(value = SituacaoProvedor.class, names = { "NAO_EMITIDA", "FALHA_EMISSAO" })
    void semCobrancaNoProvedorPermiteEditar(SituacaoProvedor situacao) {
        assertThat(roteador.permiteEditar(Cobranca.builder().situacaoProvedor(situacao).build())).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SituacaoProvedor.class, names = { "EMITINDO", "EMITIDA" })
    void depoisDoPagarNaoPermiteEditar(SituacaoProvedor situacao) {
        assertThat(roteador.permiteEditar(Cobranca.builder().situacaoProvedor(situacao).build())).isFalse();
    }

    @Test
    void cancelarNoProvedorPassaParaORoteadorDePagamento() {
        Cobranca cobranca = Cobranca.builder().situacaoProvedor(SituacaoProvedor.EMITIDA).build();

        roteador.cancelarNoProvedor(cobranca);

        verify(roteadorPagamento).cancelarNoProvedor(cobranca);
    }
}
