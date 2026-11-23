package br.pucminas.eduka.backend.pagamento;

import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.escola.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.financeiro.CobrancaNaoPagavelException;
import br.pucminas.eduka.backend.exception.financeiro.SandboxIndisponivelException;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.repository.escola.FaturaPlataformaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Atalhos de teste do Sandbox (NOVA-6), por tras do
 * {@link PaymentRoutingService}: aprovar a conta filha da escola e confirmar
 * o pagamento de uma cobranca ou de uma fatura da plataforma ja emitida.
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
    private final FaturaPlataformaRepository faturaRepository;
    private final RoteadorEventos eventos;
    private final IsolamentoEscola isolamentoEscola;
    private final PiiCryptoService piiCryptoService;
    private final ChamadorProvedor provedor;
    private final TransactionTemplate transacao;

    RoteadorSandbox(ContaRecebimentoEscolaRepository contaRepository, CobrancaRepository cobrancaRepository,
            AlunoRepository alunoRepository, FaturaPlataformaRepository faturaRepository, RoteadorEventos eventos,
            IsolamentoEscola isolamentoEscola, PiiCryptoService piiCryptoService, ChamadorProvedor provedor,
            PlatformTransactionManager transactionManager) {
        this.contaRepository = contaRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.alunoRepository = alunoRepository;
        this.faturaRepository = faturaRepository;
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
            // R8: a chave so e decifrada aqui, fora da transacao, na hora da chamada.
            CredencialProvedor credencial = credencialDe(emitida);
            noSandbox(() -> provedor.executar(p -> p.sandboxConfirmarPagamento(credencial,
                    emitida.idCobrancaExterna())));
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
        // R8: a chave so e decifrada aqui, fora da transacao, na hora da chamada.
        pagarEGravar(credencialDe(emitida), emitida.idCobrancaExterna(), "cobranca " + cobrancaId,
                pago -> eventos.registrarPagamentoEncontrado(cobrancaId, pago));
    }

    /**
     * O MASTER paga no Sandbox a fatura do Eduka da propria escola, como se
     * tivesse pago o PIX ou o boleto no banco. A fatura fica na conta-pai, entao
     * o valor cai no saldo do Eduka. Grava o pagamento na hora, como no
     * {@link #pagarComoAluno}.
     *
     * @throws RecursoNaoEncontradoException fatura de outra escola (R2)
     * @throws CobrancaNaoPagavelException   fatura cancelada
     * @throws IllegalArgumentException      fatura ainda nao emitida no provedor
     */
    void pagarFatura(UUID faturaId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        String idCobrancaExterna = transacao.execute(status -> {
            FaturaPlataforma fatura = faturaRepository.findByIdAndEscolaId(faturaId, escolaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura nao encontrada"));
            if (fatura.getStatus() == StatusCobranca.PAGA) {
                return null;
            }
            if (!fatura.getStatus().emAberto()) {
                throw new CobrancaNaoPagavelException();
            }
            if (fatura.getSituacaoProvedor() != SituacaoProvedor.EMITIDA) {
                throw new IllegalArgumentException("A fatura ainda nao foi emitida no provedor");
            }
            return fatura.getIdCobrancaExterna();
        });
        if (idCobrancaExterna == null) {
            return;
        }
        CredencialProvedor credencial = provedor.chamar(PaymentProvider::credencialDaContaPai);
        pagarEGravar(credencial, idCobrancaExterna, "fatura " + faturaId,
                pago -> eventos.registrarPagamentoDaFaturaEncontrado(faturaId, pago));
    }

    /**
     * Confirma no Sandbox e grava o pagamento que o provedor devolver, sem
     * esperar o aviso.
     *
     * @param gravar grava o pagamento no Eduka; nada muda se o aviso ja gravou
     */
    private void pagarEGravar(CredencialProvedor credencial, String idCobrancaExterna, String descricao,
            Consumer<CobrancaProvedor> gravar) {
        try {
            noSandbox(() -> provedor.executar(p -> p.sandboxConfirmarPagamento(credencial, idCobrancaExterna)));
        } catch (ProvedorRecusouException e) {
            // O Asaas recusa confirmar a cobranca que ja recebeu (clique repetido, ou o pagamento confirmado
            // antes nao chegou ao Eduka porque a consulta falhou e o aviso nao veio). Se ja esta paga, grava.
            CobrancaProvedor noProvedor = consultar(credencial, idCobrancaExterna);
            if (!RoteadorFatura.pago(noProvedor)) {
                throw e;
            }
            gravar.accept(noProvedor);
            return;
        }
        try {
            CobrancaProvedor noProvedor = consultar(credencial, idCobrancaExterna);
            if (RoteadorFatura.pago(noProvedor)) {
                gravar.accept(noProvedor);
            }
        } catch (RuntimeException e) {
            // Confirmado no provedor: o aviso ou o proximo clique ainda grava o pagamento.
            log.warn("Pagamento da {} confirmado no Sandbox, mas a gravacao falhou: {}", descricao,
                    e.getClass().getSimpleName());
        }
    }

    private CobrancaProvedor consultar(CredencialProvedor credencial, String idCobrancaExterna) {
        return provedor.chamar(p -> p.consultarCobranca(credencial, idCobrancaExterna));
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
