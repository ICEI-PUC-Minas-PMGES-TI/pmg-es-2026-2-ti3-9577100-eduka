package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.StatusCobrancaProvedor;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository.EmissaoSemResposta;
import br.pucminas.eduka.backend.repository.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.security.PiiCryptoService;

/** Reconciliacao: busca pela referencia na conta certa, sem parar numa falha. */
@ExtendWith(MockitoExtension.class)
class RoteadorReconciliacaoTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T14:00:00Z");
    private static final Instant INICIADA_ANTES = AGORA.minus(RoteadorReconciliacao.EMISSAO_SEM_RESPOSTA);
    private static final byte[] CIFRADA = { 1, 2, 3 };
    private static final CredencialProvedor DA_ESCOLA = new CredencialProvedor("chave-da-escola");
    private static final CredencialProvedor DA_CONTA_PAI = new CredencialProvedor("chave-da-conta-pai");

    @Mock
    private CobrancaRepository cobrancaRepository;
    @Mock
    private FaturaPlataformaRepository faturaRepository;
    @Mock
    private RoteadorEventos eventos;
    @Mock
    private PiiCryptoService piiCryptoService;
    @Mock
    private PaymentProvider provider;

    private RoteadorReconciliacao roteador;

    @BeforeEach
    void setUp() {
        roteador = new RoteadorReconciliacao(cobrancaRepository, faturaRepository, eventos, piiCryptoService,
                new ChamadorProvedor(provider), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void cobrancaEBuscadaPelaReferenciaNaContaDaEscola() {
        UUID cobranca = UUID.randomUUID();
        Optional<CobrancaProvedor> achada = Optional.of(cobrancaProvedor("EDK-B-" + cobranca));
        when(cobrancaRepository.buscarEmissoesSemResposta(INICIADA_ANTES, 50)).thenReturn(List.of(emissao(cobranca)));
        when(piiCryptoService.decifrar(CIFRADA)).thenReturn("chave-da-escola");
        when(provider.buscarCobrancaPorReferencia(DA_ESCOLA, "EDK-B-" + cobranca)).thenReturn(achada);
        when(eventos.reconciliarCobranca(cobranca, achada)).thenReturn(true);

        assertThat(roteador.reconciliar(50)).isEqualTo(1);
    }

    @Test
    void provedorForaDoArDeixaEmitindoESegueParaAProxima() {
        UUID fora = UUID.randomUUID();
        UUID seguinte = UUID.randomUUID();
        when(cobrancaRepository.buscarEmissoesSemResposta(INICIADA_ANTES, 50))
                .thenReturn(List.of(emissao(fora), emissao(seguinte)));
        when(piiCryptoService.decifrar(CIFRADA)).thenReturn("chave-da-escola");
        when(provider.buscarCobrancaPorReferencia(DA_ESCOLA, "EDK-B-" + fora))
                .thenThrow(new ProvedorIndisponivelException("Tempo esgotado"));
        when(provider.buscarCobrancaPorReferencia(DA_ESCOLA, "EDK-B-" + seguinte)).thenReturn(Optional.empty());
        when(eventos.reconciliarCobranca(seguinte, Optional.empty())).thenReturn(true);

        assertThat(roteador.reconciliar(50)).isEqualTo(1);

        verify(eventos, never()).reconciliarCobranca(eq(fora), any());
    }

    @Test
    void cobrancaQueOAvisoJaResolveuNaoConta() {
        UUID cobranca = UUID.randomUUID();
        when(cobrancaRepository.buscarEmissoesSemResposta(INICIADA_ANTES, 50)).thenReturn(List.of(emissao(cobranca)));
        when(piiCryptoService.decifrar(CIFRADA)).thenReturn("chave-da-escola");
        when(provider.buscarCobrancaPorReferencia(DA_ESCOLA, "EDK-B-" + cobranca)).thenReturn(Optional.empty());
        when(eventos.reconciliarCobranca(cobranca, Optional.empty())).thenReturn(false);

        assertThat(roteador.reconciliar(50)).isZero();
    }

    @Test
    void faturaEBuscadaPelaReferenciaNaContaPai() {
        UUID fatura = UUID.randomUUID();
        Optional<CobrancaProvedor> achada = Optional.of(cobrancaProvedor("EDK-A-" + fatura));
        when(faturaRepository.buscarEmissoesSemResposta(INICIADA_ANTES, PageRequest.of(0, 50)))
                .thenReturn(List.of(fatura));
        when(provider.credencialDaContaPai()).thenReturn(DA_CONTA_PAI);
        when(provider.buscarCobrancaPorReferencia(DA_CONTA_PAI, "EDK-A-" + fatura)).thenReturn(achada);
        when(eventos.reconciliarFatura(fatura, achada)).thenReturn(true);

        assertThat(roteador.reconciliar(50)).isEqualTo(1);
    }

    @Test
    void semFaturaPresaNaoPedeACredencialDaContaPai() {
        when(faturaRepository.buscarEmissoesSemResposta(INICIADA_ANTES, PageRequest.of(0, 50))).thenReturn(List.of());

        assertThat(roteador.reconciliar(50)).isZero();

        verify(provider, never()).credencialDaContaPai();
        verifyNoInteractions(eventos);
    }

    @Test
    void contaPaiNaoConfiguradaDeixaAsFaturasEmitindo() {
        when(faturaRepository.buscarEmissoesSemResposta(INICIADA_ANTES, PageRequest.of(0, 50)))
                .thenReturn(List.of(UUID.randomUUID()));
        when(provider.credencialDaContaPai()).thenThrow(new ProvedorNaoConfiguradoException("Sem chave"));

        assertThat(roteador.reconciliar(50)).isZero();

        verifyNoInteractions(eventos);
    }

    private static EmissaoSemResposta emissao(UUID id) {
        return new EmissaoSemResposta() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public byte[] getCredencialCifrada() {
                return CIFRADA;
            }
        };
    }

    private static CobrancaProvedor cobrancaProvedor(String referencia) {
        return new CobrancaProvedor("pay_reconciliada", referencia, StatusCobrancaProvedor.AGUARDANDO_PAGAMENTO,
                FormaPagamento.PIX, new BigDecimal("150.00"), null, null, null, null, null, null);
    }
}
