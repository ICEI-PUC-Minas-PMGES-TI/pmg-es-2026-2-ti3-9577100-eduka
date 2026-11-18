package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.EventoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.OrigemPagamento;
import br.pucminas.eduka.backend.domain.financeiro.PagamentoCobranca;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.financeiro.StatusEventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.EventoProvedorRepository;
import br.pucminas.eduka.backend.repository.financeiro.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.service.financeiro.CobrancaService;

/**
 * O que o pagamento confia ao banco e os testes com mock nao enxergam: as
 * constraints que barram aviso e pagamento repetidos, as queries nativas do
 * webhook e das rotinas, o isolamento do {@code @TenantId} e a trava otimista
 * da cobranca.
 */
class PagamentoBancoRealTest extends BancoRealTest {

    @Autowired
    private EventoProvedorRepository eventoRepository;
    @Autowired
    private PagamentoCobrancaRepository pagamentoRepository;
    @Autowired
    private CobrancaRepository cobrancaRepository;
    @Autowired
    private CobrancaService cobrancaService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private PiiCryptoService pii;

    private static EventoProvedor evento(String idEventoExterno) {
        return EventoProvedor.builder()
                .idEventoExterno(idEventoExterno)
                .tipoOriginal("PAYMENT_RECEIVED")
                .tipo(TipoEventoProvedor.PAGAMENTO_RECEBIDO)
                .payload("{}")
                .status(StatusEventoProvedor.RECEBIDO)
                .recebidoEm(Instant.now())
                .build();
    }

    private static PagamentoCobranca pagamento(UUID escolaId, UUID cobrancaId) {
        return PagamentoCobranca.builder()
                .escolaId(escolaId)
                .cobrancaId(cobrancaId)
                .valorPago(new BigDecimal("100.00"))
                .dataPagamento(LocalDate.now())
                .formaPagamento(FormaPagamento.PIX)
                .origem(OrigemPagamento.ONLINE)
                .idTransacaoExterna("pay_" + DadosDeTeste.unico())
                .build();
    }

    // ---------- Aviso e pagamento uma vez so ----------

    @Test
    void avisoRepetidoDoProvedorEBarradoPeloBanco() {
        String idEvento = "evt_" + DadosDeTeste.unico();
        eventoRepository.saveAndFlush(evento(idEvento));

        assertThatThrownBy(() -> eventoRepository.saveAndFlush(evento(idEvento)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(eventoRepository.existsByIdEventoExterno(idEvento)).isTrue();
    }

    @Test
    void agendadorPegaSoOsAvisosNaoTerminadosComTentativasSobrandoEForaDaJanela() {
        EventoProvedor recebido = eventoRepository.saveAndFlush(evento("evt_" + DadosDeTeste.unico()));
        EventoProvedor comErro = evento("evt_" + DadosDeTeste.unico());
        comErro.setStatus(StatusEventoProvedor.ERRO);
        comErro.setTentativas(3);
        comErro = eventoRepository.saveAndFlush(comErro);
        EventoProvedor esgotado = evento("evt_" + DadosDeTeste.unico());
        esgotado.setStatus(StatusEventoProvedor.ERRO);
        esgotado.setTentativas(10);
        esgotado = eventoRepository.saveAndFlush(esgotado);
        EventoProvedor processado = evento("evt_" + DadosDeTeste.unico());
        processado.setStatus(StatusEventoProvedor.PROCESSADO);
        processado = eventoRepository.saveAndFlush(processado);
        List<StatusEventoProvedor> pendentes = List.of(StatusEventoProvedor.RECEBIDO,
                StatusEventoProvedor.PROCESSANDO, StatusEventoProvedor.ERRO);

        List<UUID> agora = eventoRepository.buscarPendentes(pendentes, 10, Instant.now().plusSeconds(60),
                PageRequest.of(0, 1000));
        List<UUID> antesDeChegarem = eventoRepository.buscarPendentes(pendentes, 10,
                Instant.now().minusSeconds(3600), PageRequest.of(0, 1000));

        assertThat(agora).contains(recebido.getId(), comErro.getId())
                .doesNotContain(esgotado.getId(), processado.getId());
        assertThat(antesDeChegarem).doesNotContain(recebido.getId(), comErro.getId());
    }

    @Test
    void cobrancaTemNoMaximoUmPagamento() {
        UUID escola = dados.escola();
        UUID cobranca = dados.cobranca(escola, dados.aluno(escola), StatusCobranca.PAGA, LocalDate.now());
        pagamentoRepository.saveAndFlush(pagamento(escola, cobranca));

        assertThatThrownBy(() -> pagamentoRepository.saveAndFlush(pagamento(escola, cobranca)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pagamentoNaoApontaParaCobrancaDeOutraEscola() {
        UUID escola = dados.escola();
        UUID outraEscola = dados.escola();
        UUID cobranca = dados.cobranca(escola, dados.aluno(escola), StatusCobranca.PENDENTE, LocalDate.now());

        assertThatThrownBy(() -> pagamentoRepository.saveAndFlush(pagamento(outraEscola, cobranca)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Queries nativas (sem usuario autenticado) ----------

    @Test
    void webhookAchaACobrancaPeloIdNoProvedor() {
        UUID escola = dados.escola();
        String idExterno = "pay_" + DadosDeTeste.unico();
        UUID cobranca = dados.cobrancaEmitida(escola, dados.aluno(escola), idExterno, FormaPagamento.BOLETO);

        List<Object[]> linhas = cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno(idExterno);

        assertThat(linhas).hasSize(1);
        Object[] linha = linhas.getFirst();
        // Ordem de COLUNAS_PAGAMENTO_ONLINE: id, escola, ..., status, forma, situacao no provedor.
        assertThat(linha[0]).isEqualTo(cobranca);
        assertThat(linha[1]).isEqualTo(escola);
        assertThat(linha[7]).isEqualTo("PENDENTE");
        assertThat(linha[8]).isEqualTo("BOLETO");
        assertThat(linha[9]).isEqualTo("EMITIDA");
        assertThat(cobrancaRepository.buscarParaPagamentoOnlinePorIdExterno("pay_inexistente")).isEmpty();
    }

    @Test
    void reconciliacaoPegaSoAsEmissoesAntigasDeEscolaComContaComACredencial() {
        Instant agora = Instant.now();
        UUID escola = dados.escola();
        dados.contaAprovada(escola, "chave-da-escola");
        UUID antiga = dados.cobrancaEmitindo(escola, dados.aluno(escola), agora.minusSeconds(3600));
        UUID recente = dados.cobrancaEmitindo(escola, dados.aluno(escola), agora);
        UUID emitida = dados.cobrancaEmitida(escola, dados.aluno(escola), "pay_" + DadosDeTeste.unico(),
                FormaPagamento.PIX);
        UUID semConta = dados.escola();
        UUID daEscolaSemConta = dados.cobrancaEmitindo(semConta, dados.aluno(semConta), agora.minusSeconds(3600));

        List<CobrancaRepository.EmissaoSemResposta> pendentes = cobrancaRepository
                .buscarEmissoesSemResposta(agora.minusSeconds(300), 1000);

        assertThat(pendentes).extracting(CobrancaRepository.EmissaoSemResposta::getId).contains(antiga)
                .doesNotContain(recente, emitida, daEscolaSemConta);
        CobrancaRepository.EmissaoSemResposta encontrada = pendentes.stream()
                .filter(p -> p.getId().equals(antiga)).findFirst().orElseThrow();
        assertThat(pii.decifrar(encontrada.getCredencialCifrada())).isEqualTo("chave-da-escola");
    }

    @Test
    void rotinaDeAtrasoMarcaSoAsPendentesVencidasDeTodasAsEscolas() {
        LocalDate vencida = LocalDate.now().minusDays(10);
        UUID escolaA = dados.escola();
        UUID escolaB = dados.escola();
        UUID vencidaA = dados.cobranca(escolaA, dados.aluno(escolaA), StatusCobranca.PENDENTE, vencida);
        UUID vencidaB = dados.cobranca(escolaB, dados.aluno(escolaB), StatusCobranca.PENDENTE, vencida);
        UUID noPrazo = dados.cobranca(escolaA, dados.aluno(escolaA), StatusCobranca.PENDENTE,
                LocalDate.now().plusDays(10));
        UUID paga = dados.cobranca(escolaA, dados.aluno(escolaA), StatusCobranca.PAGA, vencida);

        assertThat(cobrancaService.marcarVencidasComoEmAtraso()).isGreaterThanOrEqualTo(2);

        assertThat(dados.statusGravado(vencidaA)).isEqualTo(StatusCobranca.EM_ATRASO);
        assertThat(dados.statusGravado(vencidaB)).isEqualTo(StatusCobranca.EM_ATRASO);
        assertThat(dados.statusGravado(noPrazo)).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(dados.statusGravado(paga)).isEqualTo(StatusCobranca.PAGA);
    }

    // ---------- Isolamento e concorrencia ----------

    @Test
    void cadaEscolaSoEnxergaAsPropriasCobrancas() {
        UUID escolaA = dados.escola();
        UUID escolaB = dados.escola();
        UUID daEscolaA = dados.cobranca(escolaA, dados.aluno(escolaA), StatusCobranca.PENDENTE, LocalDate.now());
        UUID daEscolaB = dados.cobranca(escolaB, dados.aluno(escolaB), StatusCobranca.PENDENTE, LocalDate.now());

        autenticarComo("FINANCEIRO", escolaA);

        // Mesmo sem filtrar pela escola na query, o @TenantId poe o escola_id do token.
        assertThat(cobrancaRepository.findAll()).extracting(Cobranca::getId)
                .contains(daEscolaA)
                .doesNotContain(daEscolaB);
        assertThat(cobrancaRepository.findById(daEscolaB)).isEmpty();
    }

    @Test
    void duasGravacoesDaMesmaCobrancaNaoSeSobrescrevem() {
        UUID escola = dados.escola();
        UUID id = dados.cobranca(escola, dados.aluno(escola), StatusCobranca.PENDENTE, LocalDate.now());
        autenticarComo("FINANCEIRO", escola);
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        // Edicao e Pagar leem a mesma versao da cobranca.
        Cobranca naEdicao = transacao.execute(status -> cobrancaRepository.findById(id).orElseThrow());
        Cobranca noPagar = transacao.execute(status -> cobrancaRepository.findById(id).orElseThrow());

        naEdicao.setValor(new BigDecimal("150.00"));
        transacao.executeWithoutResult(status -> cobrancaRepository.saveAndFlush(naEdicao));

        noPagar.setValor(new BigDecimal("999.00"));
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> cobrancaRepository.saveAndFlush(noPagar)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(dados.valor(id)).isEqualByComparingTo("150.00");
    }
}
