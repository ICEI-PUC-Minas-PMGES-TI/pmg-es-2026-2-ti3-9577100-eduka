package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.AtualizarCobrancaRequest;
import br.pucminas.eduka.backend.dto.DadosPagamentoResponse;
import br.pucminas.eduka.backend.exception.CobrancaNaoEditavelException;
import br.pucminas.eduka.backend.exception.CobrancaNaoPagavelException;
import br.pucminas.eduka.backend.exception.PagamentoEmProcessamentoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.integracao.DadosDeTeste.AlunoComAcesso;
import br.pucminas.eduka.backend.pagamento.PaymentProvider;
import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.pagamento.ProvedorIndisponivelException;
import br.pucminas.eduka.backend.pagamento.ProvedorRecusouException;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.PixProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.service.CobrancaService;

/**
 * O Pagar do aluno com o banco de verdade e o Asaas simulado: a trava que
 * impede duas cobrancas no Asaas, o que fica gravado quando o Asaas cai ou
 * recusa, e que ninguem paga cobranca de outra escola ou de outro aluno.
 */
class PagarCobrancaBancoRealTest extends BancoRealTest {

    /** CPF valido (digitos verificadores conferem). */
    private static final String CPF = "52998224725";

    @MockitoBean
    private PaymentProvider provider;

    @Autowired
    private PaymentRoutingService paymentRoutingService;
    @Autowired
    private CobrancaService cobrancaService;

    private UUID escola;
    private AlunoComAcesso aluno;
    private UUID cobranca;

    @BeforeEach
    void escolaQueRecebePeloAsaas() {
        escola = dados.escola();
        dados.contaAprovada(escola);
        aluno = dados.alunoComAcesso(escola, "Ana Souza", CPF);
        cobranca = dados.cobranca(escola, aluno.alunoId(), StatusCobranca.PENDENTE, LocalDate.now().plusDays(10));
    }

    private void asaasNoAr() {
        willReturn("cus_" + DadosDeTeste.unico()).given(provider).obterOuCriarCliente(any(), any());
        willAnswer(chamada -> criadaNoAsaas()).given(provider).criarCobranca(any(), any());
        willReturn(new PixProvedor("qr-base64", "copia-e-cola", null)).given(provider).obterPix(any(), anyString());
    }

    private static CobrancaProvedor criadaNoAsaas() {
        return new CobrancaProvedor("pay_" + DadosDeTeste.unico(), null, StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO,
                FormaPagamento.PIX, new BigDecimal("100.00"), null, null, null, null, null, null);
    }

    private DadosPagamentoResponse pagarComoAluno() {
        autenticarComo(aluno.usuarioId(), "ALUNO", escola);
        return paymentRoutingService.pagarCobranca(cobranca, FormaPagamento.PIX);
    }

    // ---------- Clique duplo ----------

    @Test
    void doisCliquesAoMesmoTempoCriamUmaCobrancaSoNoAsaas() throws Exception {
        asaasNoAr();
        // Quem chega ao Asaas espera o outro clique terminar: os dois correm de verdade ao mesmo tempo.
        CountDownLatch umTerminou = new CountDownLatch(1);
        willAnswer(chamada -> {
            assertThat(umTerminou.await(10, TimeUnit.SECONDS)).as("o outro clique terminou").isTrue();
            return criadaNoAsaas();
        }).given(provider).criarCobranca(any(), any());
        CyclicBarrier largada = new CyclicBarrier(2);

        List<Throwable> falhas = new ArrayList<>();
        int sucessos = 0;
        ExecutorService cliques = Executors.newFixedThreadPool(2);
        try {
            List<Future<DadosPagamentoResponse>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(cliques.submit(() -> {
                    try {
                        largada.await(10, TimeUnit.SECONDS);
                        return pagarComoAluno();
                    } finally {
                        umTerminou.countDown();
                    }
                }));
            }
            for (Future<DadosPagamentoResponse> resultado : resultados) {
                try {
                    assertThat(resultado.get(30, TimeUnit.SECONDS).pronto()).isTrue();
                    sucessos++;
                } catch (ExecutionException e) {
                    falhas.add(e.getCause());
                }
            }
        } finally {
            cliques.shutdownNow();
        }

        assertThat(sucessos).isEqualTo(1);
        assertThat(falhas).singleElement().isInstanceOf(PagamentoEmProcessamentoException.class);
        verify(provider, times(1)).obterOuCriarCliente(any(), any());
        verify(provider, times(1)).criarCobranca(any(), any());
        assertThat(dados.situacaoNoProvedor(cobranca)).isEqualTo(SituacaoProvedor.EMITIDA);
    }

    @Test
    void segundoCliqueDepoisDeEmitidaReaproveitaACobrancaDoAsaas() {
        asaasNoAr();

        pagarComoAluno();
        DadosPagamentoResponse deNovo = pagarComoAluno();

        assertThat(deNovo.pronto()).isTrue();
        verify(provider, times(1)).criarCobranca(any(), any());
        verify(provider, times(2)).obterPix(any(), anyString());
    }

    // ---------- Asaas fora do ar ou recusando ----------

    @Test
    void asaasForaDoArAoCadastrarOPagadorLiberaParaTentarDeNovo() {
        willThrow(new ProvedorIndisponivelException("timeout")).given(provider).obterOuCriarCliente(any(), any());

        assertThatThrownBy(this::pagarComoAluno).isInstanceOf(ProvedorIndisponivelException.class);

        // Nada foi pedido ao Asaas: a cobranca nao fica presa em EMITINDO nem e paga.
        assertThat(dados.situacaoNoProvedor(cobranca)).isEqualTo(SituacaoProvedor.FALHA_EMISSAO);
        assertThat(dados.ultimoErroProvedor(cobranca)).isEqualTo("Provedor de pagamento indisponivel");
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(dados.pagamentosDaCobranca(cobranca)).isZero();
        verify(provider, never()).criarCobranca(any(), any());

        asaasNoAr();
        assertThat(pagarComoAluno().pronto()).isTrue();
        assertThat(dados.situacaoNoProvedor(cobranca)).isEqualTo(SituacaoProvedor.EMITIDA);
        assertThat(dados.ultimoErroProvedor(cobranca)).isNull();
    }

    @Test
    void asaasSemResponderNaCriacaoDeixaEmProcessamentoSemCriarDeNovo() {
        asaasNoAr();
        willThrow(new ProvedorIndisponivelException("timeout")).given(provider).criarCobranca(any(), any());

        DadosPagamentoResponse resposta = pagarComoAluno();

        // Nao se sabe se o Asaas criou: a cobranca fica EMITINDO para a reconciliacao.
        assertThat(resposta.situacao()).isEqualTo(DadosPagamentoResponse.PROCESSANDO);
        assertThat(dados.situacaoNoProvedor(cobranca)).isEqualTo(SituacaoProvedor.EMITINDO);

        assertThatThrownBy(this::pagarComoAluno).isInstanceOf(PagamentoEmProcessamentoException.class);
        verify(provider, times(1)).criarCobranca(any(), any());

        // E a escola nao edita o que pode ja existir no Asaas.
        autenticarComo("FINANCEIRO", escola);
        assertThatThrownBy(() -> cobrancaService.atualizar(cobranca, new AtualizarCobrancaRequest(null, null,
                "Mensalidade", new BigDecimal("200.00"), LocalDate.now().plusDays(10))))
                .isInstanceOf(CobrancaNaoEditavelException.class);
        assertThat(dados.valor(cobranca)).isEqualByComparingTo("100.00");
    }

    @Test
    void recusaDoAsaasFicaGravadaEOProximoCliqueTentaDeNovo() {
        asaasNoAr();
        willThrow(new ProvedorRecusouException("CPF do pagador invalido")).given(provider).criarCobranca(any(), any());

        assertThatThrownBy(this::pagarComoAluno).isInstanceOf(ProvedorRecusouException.class);

        assertThat(dados.situacaoNoProvedor(cobranca)).isEqualTo(SituacaoProvedor.FALHA_EMISSAO);
        assertThat(dados.ultimoErroProvedor(cobranca)).isEqualTo("CPF do pagador invalido");

        willAnswer(chamada -> criadaNoAsaas()).given(provider).criarCobranca(any(), any());
        assertThat(pagarComoAluno().pronto()).isTrue();
        verify(provider, times(2)).criarCobranca(any(), any());
    }

    // ---------- Quem pode pagar o que ----------

    @Test
    void alunoDeOutraEscolaNaoPagaSemChegarAoAsaas() {
        UUID outraEscola = dados.escola();
        dados.contaAprovada(outraEscola);
        AlunoComAcesso deOutraEscola = dados.alunoComAcesso(outraEscola, "Bruno Lima", CPF);
        autenticarComo(deOutraEscola.usuarioId(), "ALUNO", outraEscola);

        assertThatThrownBy(() -> paymentRoutingService.pagarCobranca(cobranca, FormaPagamento.PIX))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(provider);
        assertThat(dados.situacaoNoProvedor(cobranca)).isEqualTo(SituacaoProvedor.NAO_EMITIDA);
    }

    @Test
    void alunoNaoPagaACobrancaDeOutroAlunoDaMesmaEscola() {
        AlunoComAcesso colega = dados.alunoComAcesso(escola, "Carla Dias", CPF);
        autenticarComo(colega.usuarioId(), "ALUNO", escola);

        assertThatThrownBy(() -> paymentRoutingService.pagarCobranca(cobranca, FormaPagamento.PIX))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(provider);
    }

    @Test
    void cobrancaPagaOuCanceladaNaoVaiAoAsaas() {
        UUID paga = dados.cobranca(escola, aluno.alunoId(), StatusCobranca.PAGA, LocalDate.now());
        UUID cancelada = dados.cobranca(escola, aluno.alunoId(), StatusCobranca.CANCELADA, LocalDate.now());
        autenticarComo(aluno.usuarioId(), "ALUNO", escola);

        assertThatThrownBy(() -> paymentRoutingService.pagarCobranca(paga, FormaPagamento.PIX))
                .isInstanceOf(CobrancaNaoPagavelException.class);
        assertThatThrownBy(() -> paymentRoutingService.pagarCobranca(cancelada, FormaPagamento.BOLETO))
                .isInstanceOf(CobrancaNaoPagavelException.class);

        verifyNoInteractions(provider);
    }
}
