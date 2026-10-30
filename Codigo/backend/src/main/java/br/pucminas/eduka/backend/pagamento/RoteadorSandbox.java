package br.pucminas.eduka.backend.pagamento;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.SandboxIndisponivelException;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Atalhos de teste do Sandbox (NOVA-6), por tras do
 * {@link PaymentRoutingService}: aprovar a conta filha da escola e confirmar
 * o pagamento de uma cobranca ja emitida.
 *
 * <p>
 * O provedor manda o aviso (webhook) como faria de verdade, e o Sincronizar
 * do MASTER le a conta aprovada. Fora do Sandbox o adaptador recusa (achado
 * A06) e o atalho responde SANDBOX_INDISPONIVEL.
 * </p>
 */
@Component
class RoteadorSandbox {

    private static final Logger log = LoggerFactory.getLogger(RoteadorSandbox.class);

    private final ContaRecebimentoEscolaRepository contaRepository;
    private final CobrancaRepository cobrancaRepository;
    private final AlunoRepository alunoRepository;
    private final RoteadorEventos eventos;
    private final IsolamentoEscola isolamentoEscola;
    private final PiiCryptoService piiCryptoService;
    private final ChamadorProvedor provedor;
    private final TransactionTemplate transacao;

    RoteadorSandbox(ContaRecebimentoEscolaRepository contaRepository, CobrancaRepository cobrancaRepository,
            AlunoRepository alunoRepository, RoteadorEventos eventos, IsolamentoEscola isolamentoEscola,
            PiiCryptoService piiCryptoService, ChamadorProvedor provedor,
            PlatformTransactionManager transactionManager) {
        this.contaRepository = contaRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.alunoRepository = alunoRepository;
        this.eventos = eventos;
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
        Emitida emitida = transacao.execute(status -> emitida(cobrancaId, escolaId, null));
        if (emitida != null) {
            confirmarNoSandbox(emitida);
        }
    }

    /**
     * O ALUNO paga a propria cobranca no Sandbox, como se tivesse pago o PIX
     * ou o boleto no banco. Depois de confirmar, consulta o provedor e grava o
     * pagamento na hora, sem esperar o aviso: em dev sem tunel o webhook nao
     * chega. Se o aviso chegar depois, nada muda. Ja paga no Eduka, nao faz
     * nada; ja paga so no provedor, grava sem confirmar de novo.
     *
     * @throws RecursoNaoEncontradoException cobranca de outro aluno ou de outra escola (R2)
     * @throws IllegalArgumentException      cobranca ainda nao emitida no provedor
     */
    void pagarComoAluno(UUID cobrancaId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        UUID escolaId = usuario.escolaId();
        Emitida emitida = transacao.execute(status -> {
            UUID alunoId = alunoRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                    .map(Aluno::getId)
                    .orElseThrow(RoteadorSandbox::cobrancaNaoEncontrada);
            return emitida(cobrancaId, escolaId, alunoId);
        });
        if (emitida == null) {
            return;
        }
        CredencialProvedor credencial;
        try {
            credencial = confirmarNoSandbox(emitida);
        } catch (ProvedorRecusouException e) {
            // O Asaas recusa confirmar a cobranca que ja recebeu (clique repetido, ou o pagamento confirmado
            // antes nao chegou ao Eduka porque a consulta falhou e o aviso nao veio). Se ja esta paga, grava.
            CobrancaProvedor noProvedor = consultar(credencialDe(emitida), emitida);
            if (!RoteadorFatura.pago(noProvedor)) {
                throw e;
            }
            eventos.registrarPagamentoEncontrado(cobrancaId, noProvedor);
            return;
        }
        try {
            CobrancaProvedor noProvedor = consultar(credencial, emitida);
            if (RoteadorFatura.pago(noProvedor)) {
                eventos.registrarPagamentoEncontrado(cobrancaId, noProvedor);
            }
        } catch (RuntimeException e) {
            // Confirmado no provedor: o aviso ou o proximo clique ainda grava o pagamento.
            log.warn("Pagamento da cobranca {} confirmado no Sandbox, mas a gravacao falhou: {}", cobrancaId,
                    e.toString());
        }
    }

    private CobrancaProvedor consultar(CredencialProvedor credencial, Emitida emitida) {
        return provedor.chamar(p -> p.consultarCobranca(credencial, emitida.idCobrancaExterna()));
    }

    /**
     * A cobranca da escola emitida no provedor, com a chave da conta filha.
     *
     * @param alunoId so o dono da cobranca, ou null para qualquer aluno da escola
     * @return null se a cobranca ja esta paga no Eduka: nada a confirmar
     */
    private Emitida emitida(UUID cobrancaId, UUID escolaId, UUID alunoId) {
        Cobranca cobranca = cobrancaRepository.findByIdAndEscolaId(cobrancaId, escolaId)
                .filter(c -> alunoId == null || c.getAluno().getId().equals(alunoId))
                .orElseThrow(RoteadorSandbox::cobrancaNaoEncontrada);
        if (cobranca.getStatus() == StatusCobranca.PAGA) {
            return null;
        }
        if (cobranca.getSituacaoProvedor() != SituacaoProvedor.EMITIDA) {
            throw new IllegalArgumentException("A cobranca ainda nao foi emitida no provedor");
        }
        ContaRecebimentoEscola conta = contaRepository.findByEscolaId(escolaId)
                .orElseThrow(() -> new IllegalStateException("Cobranca emitida sem conta de recebimento"));
        return new Emitida(cobranca.getIdCobrancaExterna(), conta.getCredencialCifrada());
    }

    /** Fora da transacao: a chave so e decifrada aqui, na hora da chamada (R8). */
    private CredencialProvedor confirmarNoSandbox(Emitida emitida) {
        CredencialProvedor credencial = credencialDe(emitida);
        noSandbox(() -> provedor.executar(p -> p.sandboxConfirmarPagamento(credencial, emitida.idCobrancaExterna())));
        return credencial;
    }

    private CredencialProvedor credencialDe(Emitida emitida) {
        return new CredencialProvedor(piiCryptoService.decifrar(emitida.credencialCifrada()));
    }

    private static RecursoNaoEncontradoException cobrancaNaoEncontrada() {
        return new RecursoNaoEncontradoException("Cobranca nao encontrada");
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
