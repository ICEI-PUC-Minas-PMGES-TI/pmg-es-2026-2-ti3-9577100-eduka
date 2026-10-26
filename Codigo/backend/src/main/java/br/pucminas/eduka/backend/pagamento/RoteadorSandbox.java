package br.pucminas.eduka.backend.pagamento;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.SandboxIndisponivelException;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;

/**
 * Atalhos de teste do Sandbox (NOVA-6), por tras do
 * {@link PaymentRoutingService}: aprovar a conta filha da escola e confirmar
 * o pagamento de uma cobranca ja emitida.
 *
 * <p>
 * Nao grava nada: o provedor manda o aviso (webhook) como faria de verdade, e
 * o Sincronizar do MASTER le a conta aprovada. Fora do Sandbox o adaptador
 * recusa (achado A06) e o atalho responde SANDBOX_INDISPONIVEL.
 * </p>
 */
@Component
class RoteadorSandbox {

    private final ContaRecebimentoEscolaRepository contaRepository;
    private final CobrancaRepository cobrancaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final PiiCryptoService piiCryptoService;
    private final ChamadorProvedor provedor;
    private final TransactionTemplate transacao;

    RoteadorSandbox(ContaRecebimentoEscolaRepository contaRepository, CobrancaRepository cobrancaRepository,
            IsolamentoEscola isolamentoEscola, PiiCryptoService piiCryptoService, ChamadorProvedor provedor,
            PlatformTransactionManager transactionManager) {
        this.contaRepository = contaRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.piiCryptoService = piiCryptoService;
        this.provedor = provedor;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    /**
     * Aprova no Sandbox a conta filha da escola do token.
     *
     * @throws RecursoNaoEncontradoException sem conta de recebimento
     * @throws IllegalArgumentException      conta ainda sem chave no provedor
     */
    void aprovarConta() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        byte[] credencialCifrada = transacao.execute(status -> {
            ContaRecebimentoEscola conta = contaRepository.findByEscolaId(escolaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Conta de recebimento nao encontrada"));
            if (conta.getCredencialCifrada() == null) {
                throw new IllegalArgumentException("A conta de recebimento ainda nao foi criada no provedor");
            }
            return conta.getCredencialCifrada();
        });
        CredencialProvedor credencial = new CredencialProvedor(piiCryptoService.decifrar(credencialCifrada));
        noSandbox(() -> provedor.executar(p -> p.sandboxAprovarConta(credencial)));
    }

    /**
     * Confirma no Sandbox o pagamento de uma cobranca da escola do token.
     *
     * @throws RecursoNaoEncontradoException cobranca de outra escola ou inexistente (R2)
     * @throws IllegalArgumentException      cobranca ainda nao emitida no provedor
     */
    void confirmarPagamento(UUID cobrancaId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Emitida emitida = transacao.execute(status -> {
            Cobranca cobranca = cobrancaRepository.findByIdAndEscolaId(cobrancaId, escolaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cobranca nao encontrada"));
            if (cobranca.getSituacaoProvedor() != SituacaoProvedor.EMITIDA) {
                throw new IllegalArgumentException("A cobranca ainda nao foi emitida no provedor");
            }
            ContaRecebimentoEscola conta = contaRepository.findByEscolaId(escolaId)
                    .orElseThrow(() -> new IllegalStateException("Cobranca emitida sem conta de recebimento"));
            return new Emitida(cobranca.getIdCobrancaExterna(), conta.getCredencialCifrada());
        });
        CredencialProvedor credencial = new CredencialProvedor(piiCryptoService.decifrar(emitida.credencialCifrada()));
        noSandbox(() -> provedor.executar(p -> p.sandboxConfirmarPagamento(credencial, emitida.idCobrancaExterna())));
    }

    /** O adaptador lanca IllegalStateException fora do Sandbox (contrato do PaymentProvider). */
    private static void noSandbox(Runnable chamada) {
        try {
            chamada.run();
        } catch (IllegalStateException e) {
            throw new SandboxIndisponivelException();
        }
    }

    private record Emitida(String idCobrancaExterna, byte[] credencialCifrada) {
    }
}
