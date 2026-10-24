package br.pucminas.eduka.backend.pagamento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.StatusContaRecebimento;
import br.pucminas.eduka.backend.dto.AtivarRecebimentosRequest;
import br.pucminas.eduka.backend.dto.ContaRecebimentoResponse;
import br.pucminas.eduka.backend.dto.DadosBancariosRequest;
import br.pucminas.eduka.backend.dto.HabilitacaoRecebimentos;
import br.pucminas.eduka.backend.dto.SituacaoContaResponse;
import br.pucminas.eduka.backend.exception.ContaRecebimentoNaoAtivavelException;
import br.pucminas.eduka.backend.exception.RecebimentosIndisponiveisException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.pagamento.tipos.ContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosBancarios;
import br.pucminas.eduka.backend.pagamento.tipos.DadosNovaConta;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoCadastral;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository.ContaParaSincronizar;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PiiCryptoService;

/**
 * Conta de recebimento da escola (conta filha no provedor), por tras do
 * {@link PaymentRoutingService}: ativar, sincronizar, consultar e a
 * habilitacao (R11).
 *
 * <p>
 * Ativar segue os tres passos da spec §4.3: reservar a conta em CRIANDO numa
 * transacao curta; criar no provedor sem transacao; gravar o resultado em
 * outra transacao curta. A chave da conta filha so e gravada cifrada, e os
 * dados bancarios vao para o provedor sem serem gravados: do banco ficam so o
 * codigo e os 4 ultimos digitos da conta.
 * </p>
 *
 * <p>
 * Resposta inconclusiva na criacao (timeout, 5xx) deixa a conta em
 * VERIFICACAO_PENDENTE: a proxima ativacao procura a conta pelo CNPJ antes de
 * criar outra. Achada assim, a conta vem sem chave (o provedor nao a devolve
 * de novo) e fica CREDENCIAL_INDISPONIVEL.
 * </p>
 */
@Component
class RoteadorConta {

    private static final Logger log = LoggerFactory.getLogger(RoteadorConta.class);

    private static final int TAMANHO_MAXIMO_ERRO = 500;

    /** CRIANDO ha mais tempo que isso ficou sem resposta (a aplicacao caiu no meio). */
    static final Duration CRIACAO_SEM_RESPOSTA = Duration.ofMinutes(5);

    static final String MENSAGEM_INCONCLUSIVA = "O provedor de pagamento nao confirmou a criacao da conta. "
            + "Envie de novo: a conta sera conferida antes de criar outra";
    static final String MENSAGEM_SEM_CREDENCIAL = "A conta ja existe no provedor, mas sem a chave de acesso. "
            + "Fale com o suporte do Eduka";

    /** Estados em que o MASTER pode (re)enviar o cadastro. */
    private static final Set<StatusContaRecebimento> ATIVAVEIS = EnumSet.of(StatusContaRecebimento.ERRO_CRIACAO,
            StatusContaRecebimento.VERIFICACAO_PENDENTE);

    /** Conta criada no provedor, com chave: a situacao pode ser consultada. */
    private static final Set<StatusContaRecebimento> SINCRONIZAVEIS = EnumSet.of(
            StatusContaRecebimento.AGUARDANDO_APROVACAO, StatusContaRecebimento.APROVADA,
            StatusContaRecebimento.REJEITADA);

    private final ContaRecebimentoEscolaRepository contaRepository;
    private final EscolaRepository escolaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final PiiCryptoService piiCryptoService;
    private final ChamadorProvedor provedor;
    private final TransactionTemplate transacao;
    private final Clock clock;

    RoteadorConta(ContaRecebimentoEscolaRepository contaRepository, EscolaRepository escolaRepository,
            IsolamentoEscola isolamentoEscola, PiiCryptoService piiCryptoService, ChamadorProvedor provedor,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.contaRepository = contaRepository;
        this.escolaRepository = escolaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.piiCryptoService = piiCryptoService;
        this.provedor = provedor;
        this.transacao = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ---- Habilitacao (R11) ----

    /**
     * R11: habilitado so com a conta APROVADA e a escola ativa. Escola inativa
     * vem primeiro, porque nem uma conta aprovada a faz receber.
     */
    HabilitacaoRecebimentos habilitacao(UUID escolaId) {
        boolean escolaAtiva = escolaRepository.findById(escolaId).map(Escola::isAtivo).orElse(false);
        if (!escolaAtiva) {
            return naoHabilitado("ESCOLA_INATIVA");
        }
        return contaRepository.findByEscolaId(escolaId)
                .map(RoteadorConta::habilitacaoDaConta)
                .orElseGet(() -> naoHabilitado("NAO_INICIADA"));
    }

    private static HabilitacaoRecebimentos habilitacaoDaConta(ContaRecebimentoEscola conta) {
        return switch (conta.getStatus()) {
            case APROVADA -> new HabilitacaoRecebimentos(true, null);
            case CRIANDO, VERIFICACAO_PENDENTE -> naoHabilitado("EM_CRIACAO");
            case ERRO_CRIACAO -> naoHabilitado("ERRO_CRIACAO");
            case AGUARDANDO_APROVACAO -> naoHabilitado("AGUARDANDO_APROVACAO");
            case REJEITADA -> naoHabilitado("REJEITADA");
            case CREDENCIAL_INDISPONIVEL -> naoHabilitado("CREDENCIAL_INDISPONIVEL");
        };
    }

    private static HabilitacaoRecebimentos naoHabilitado(String motivo) {
        return new HabilitacaoRecebimentos(false, motivo);
    }

    /**
     * Status local a partir da situacao geral do cadastro no provedor. Os
     * avisos trazem a foto atual da conta, nao a do momento do aviso (achado
     * A11): o status vem sempre da situacao, nunca do nome do aviso.
     */
    static StatusContaRecebimento statusDaSituacao(SituacaoContaProvedor situacao) {
        if (situacao.geral() == SituacaoCadastral.APROVADO) {
            return StatusContaRecebimento.APROVADA;
        }
        if (situacao.geral() == SituacaoCadastral.REJEITADO) {
            return StatusContaRecebimento.REJEITADA;
        }
        return StatusContaRecebimento.AGUARDANDO_APROVACAO;
    }

    // ---- Consultar (MASTER da escola do token) ----

    ContaRecebimentoResponse consultar() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Escola escola = escolaRepository.findById(escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Escola nao encontrada"));
        HabilitacaoRecebimentos habilitacao = habilitacao(escolaId);
        return contaRepository.findByEscolaId(escolaId)
                .map(conta -> resposta(conta, escola, habilitacao))
                .orElseGet(() -> naoIniciada(escola, habilitacao));
    }

    // ---- Ativar (spec §8.1) ----

    /**
     * Cria a conta filha da escola do token com o cadastro informado.
     *
     * @return a conta como ficou: AGUARDANDO_APROVACAO ou APROVADA quando o
     *         provedor criou; VERIFICACAO_PENDENTE quando nao respondeu a tempo;
     *         CREDENCIAL_INDISPONIVEL quando a conta ja existia pelo CNPJ
     * @throws ContaRecebimentoNaoAtivavelException criacao em andamento ou conta ja criada
     * @throws ProvedorRecusouException             cadastro recusado; a conta fica ERRO_CRIACAO
     */
    ContaRecebimentoResponse ativar(AtivarRecebimentosRequest dados) {
        Reserva reserva = reservar(dados);

        ContaProvedor criada;
        try {
            if (reserva.procurarAntes()) {
                Optional<ContaProvedor> existente = provedor.chamar(p -> p.buscarContaPorCnpj(reserva.cnpj()));
                if (existente.isPresent()) {
                    gravarSemCredencial(reserva, existente.get());
                    return consultar();
                }
            }
            criada = provedor.chamar(p -> p.criarConta(reserva.novaConta()));
        } catch (ProvedorIndisponivelException e) {
            // Inconclusivo: a conta pode ter sido criada. A proxima ativacao procura pelo CNPJ.
            log.warn("Criacao da conta de recebimento {} no provedor ficou inconclusiva", reserva.contaId());
            gravarFalha(reserva, StatusContaRecebimento.VERIFICACAO_PENDENTE, MENSAGEM_INCONCLUSIVA);
            return consultar();
        } catch (ProvedorRecusouException | ProvedorNaoConfiguradoException e) {
            // Recusa na busca nao diz se a conta existe: continua conferindo na proxima ativacao.
            StatusContaRecebimento status = reserva.procurarAntes() ? StatusContaRecebimento.VERIFICACAO_PENDENTE
                    : StatusContaRecebimento.ERRO_CRIACAO;
            gravarFalha(reserva, status, mensagemDaFalha(e));
            throw e;
        }

        if (criada.credencial() == null || !criada.credencial().preenchida()) {
            gravarSemCredencial(reserva, criada);
            return consultar();
        }
        gravarCriada(reserva, criada);
        try {
            sincronizar(criada.idContaExterna(), criada.credencial());
        } catch (ProvedorIndisponivelException | ProvedorRecusouException | ProvedorNaoConfiguradoException e) {
            // A conta ja esta criada e guardada; a rotina le a situacao depois.
            log.warn("Situacao da conta de recebimento {} nao lida logo apos a criacao", reserva.contaId());
        }
        return consultar();
    }

    private Reserva reservar(AtivarRecebimentosRequest dados) {
        try {
            return transacao.execute(status -> reservarNaTransacao(dados));
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            // Outro envio da mesma escola gravou a conta entre a leitura e a gravacao.
            throw ContaRecebimentoNaoAtivavelException.emAndamento();
        }
    }

    private Reserva reservarNaTransacao(AtivarRecebimentosRequest dados) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Escola escola = escolaRepository.findById(escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Escola nao encontrada"));
        if (!escola.isAtivo()) {
            throw new RecebimentosIndisponiveisException("ESCOLA_INATIVA");
        }
        String cnpj = digitos(escola.getCnpjCpf());
        if (cnpj.length() != 14) {
            throw new IllegalArgumentException("A conta de recebimento exige o CNPJ da escola no cadastro");
        }

        Optional<ContaRecebimentoEscola> existente = contaRepository.findByEscolaId(escolaId);
        if (existente.isPresent()) {
            StatusContaRecebimento atual = existente.get().getStatus();
            if (atual == StatusContaRecebimento.CRIANDO) {
                throw ContaRecebimentoNaoAtivavelException.emAndamento();
            }
            if (!ATIVAVEIS.contains(atual)) {
                throw ContaRecebimentoNaoAtivavelException.jaCriada();
            }
        }
        boolean procurarAntes = existente
                .map(c -> c.getStatus() == StatusContaRecebimento.VERIFICACAO_PENDENTE)
                .orElse(false);

        Instant agora = clock.instant();
        DadosBancariosRequest banco = dados.dadosBancarios();
        ContaRecebimentoEscola conta = existente.orElseGet(() -> ContaRecebimentoEscola.builder()
                .escolaId(escolaId)
                .build());
        conta.setStatus(StatusContaRecebimento.CRIANDO);
        conta.setRazaoSocial(dados.razaoSocial().trim());
        conta.setEmailFinanceiro(dados.emailFinanceiro().trim());
        conta.setTelefoneCelular(dados.telefoneCelular());
        conta.setTipoEmpresa(dados.tipoEmpresa());
        conta.setFaturamentoMensal(dados.faturamentoMensal());
        conta.setLogradouro(dados.logradouro().trim());
        conta.setNumero(dados.numero().trim());
        conta.setComplemento(vazioComoNulo(dados.complemento()));
        conta.setBairro(dados.bairro().trim());
        conta.setCep(dados.cep());
        // Agencia, conta e digito nao sao gravados: so o banco e os 4 ultimos digitos, para exibir.
        conta.setBancoCodigo(banco.codigoBanco());
        conta.setContaFinal(quatroUltimos(banco.conta()));
        conta.setUltimoErro(null);
        conta.setSolicitadaEm(agora);
        if (existente.isPresent()) {
            conta.setAtualizadoEm(agora);
        }
        contaRepository.saveAndFlush(conta);

        DadosNovaConta novaConta = new DadosNovaConta(conta.getRazaoSocial(), conta.getEmailFinanceiro(), cnpj,
                conta.getTelefoneCelular(), conta.getTipoEmpresa(), conta.getFaturamentoMensal(),
                conta.getLogradouro(), conta.getNumero(), conta.getComplemento(), conta.getBairro(), conta.getCep(),
                new DadosBancarios(banco.codigoBanco(), banco.agencia(), banco.conta(), banco.digitoConta(),
                        banco.tipo()));
        return new Reserva(conta.getId(), cnpj, procurarAntes, novaConta);
    }

    private void gravarCriada(Reserva reserva, ContaProvedor criada) {
        transacao.executeWithoutResult(status -> {
            ContaRecebimentoEscola conta = recarregar(reserva);
            conta.setStatus(StatusContaRecebimento.AGUARDANDO_APROVACAO);
            conta.setIdContaExterna(criada.idContaExterna());
            conta.setWalletId(criada.walletId());
            // R8: a chave so e gravada cifrada.
            conta.setCredencialCifrada(piiCryptoService.cifrar(criada.credencial().valor()));
            conta.setUltimoErro(null);
            conta.setAtualizadoEm(clock.instant());
            contaRepository.saveAndFlush(conta);
        });
    }

    /** Conta achada no provedor sem a chave: so o grupo resolve (P-13). */
    private void gravarSemCredencial(Reserva reserva, ContaProvedor conhecida) {
        log.warn("Conta de recebimento {} existe no provedor sem chave de acesso", reserva.contaId());
        transacao.executeWithoutResult(status -> {
            ContaRecebimentoEscola conta = recarregar(reserva);
            conta.setStatus(StatusContaRecebimento.CREDENCIAL_INDISPONIVEL);
            conta.setIdContaExterna(conhecida.idContaExterna());
            conta.setWalletId(conhecida.walletId());
            conta.setUltimoErro(MENSAGEM_SEM_CREDENCIAL);
            conta.setAtualizadoEm(clock.instant());
            contaRepository.saveAndFlush(conta);
        });
    }

    private void gravarFalha(Reserva reserva, StatusContaRecebimento novoStatus, String mensagem) {
        transacao.executeWithoutResult(status -> {
            ContaRecebimentoEscola conta = recarregar(reserva);
            if (conta.getStatus() == StatusContaRecebimento.CRIANDO) {
                conta.setStatus(novoStatus);
                conta.setUltimoErro(mensagem);
                conta.setAtualizadoEm(clock.instant());
                contaRepository.saveAndFlush(conta);
            }
        });
    }

    private ContaRecebimentoEscola recarregar(Reserva reserva) {
        return contaRepository.findById(reserva.contaId())
                .orElseThrow(() -> new IllegalStateException("Conta de recebimento sumiu: " + reserva.contaId()));
    }

    // ---- Sincronizar (R10) ----

    /**
     * Botao Sincronizar do MASTER: le a situacao da conta no provedor e grava.
     * Conta ainda nao criada no provedor volta como esta, sem chamar o provedor.
     */
    ContaRecebimentoResponse sincronizarDaEscola() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        ContaRecebimentoEscola conta = contaRepository.findByEscolaId(escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta de recebimento nao encontrada"));
        if (SINCRONIZAVEIS.contains(conta.getStatus()) && conta.getCredencialCifrada() != null) {
            CredencialProvedor credencial = new CredencialProvedor(
                    piiCryptoService.decifrar(conta.getCredencialCifrada()));
            sincronizar(conta.getIdContaExterna(), credencial);
        }
        return consultar();
    }

    /**
     * Rotina: le a situacao das contas que esperam aprovacao e destrava as que
     * ficaram em CRIANDO. Uma conta com erro nao para as outras.
     *
     * @return quantas contas foram sincronizadas
     */
    int sincronizarAguardando() {
        Instant agora = clock.instant();
        int liberadas = transacao.execute(status -> contaRepository
                .liberarCriacaoSemResposta(agora.minus(CRIACAO_SEM_RESPOSTA), MENSAGEM_INCONCLUSIVA, agora));
        if (liberadas > 0) {
            log.warn("{} conta(s) de recebimento sem resposta da criacao foram para VERIFICACAO_PENDENTE", liberadas);
        }

        List<ContaParaSincronizar> contas = contaRepository.listarAguardandoAprovacao();
        int sincronizadas = 0;
        for (ContaParaSincronizar conta : contas) {
            try {
                CredencialProvedor credencial = new CredencialProvedor(
                        piiCryptoService.decifrar(conta.getCredencialCifrada()));
                sincronizar(conta.getIdContaExterna(), credencial);
                sincronizadas++;
            } catch (RuntimeException e) {
                log.warn("Nao foi possivel sincronizar a conta de recebimento {}: {}", conta.getIdContaExterna(),
                        e.getClass().getSimpleName());
            }
        }
        return sincronizadas;
    }

    /**
     * Le a situacao no provedor [HTTP] e grava [T]. Conta aprovada ganha a
     * chave PIX, para o primeiro QR Code nao falhar (achado A1).
     */
    private StatusContaRecebimento sincronizar(String idContaExterna, CredencialProvedor credencial) {
        SituacaoContaProvedor situacao = provedor.chamar(p -> p.consultarSituacaoConta(credencial));
        StatusContaRecebimento status = statusDaSituacao(situacao);
        transacao.executeWithoutResult(t -> contaRepository.gravarSituacao(idContaExterna, status.name(),
                nome(situacao.geral()), nome(situacao.documentacao()), nome(situacao.dadosComerciais()),
                nome(situacao.contaBancaria()), clock.instant()));
        if (status == StatusContaRecebimento.APROVADA) {
            garantirChavePix(idContaExterna, credencial);
        }
        return status;
    }

    /** Falha aqui nao desfaz nada: o Sincronizar seguinte tenta de novo (achado A17). */
    private void garantirChavePix(String idContaExterna, CredencialProvedor credencial) {
        try {
            provedor.executar(p -> p.garantirChavePix(credencial));
        } catch (ProvedorIndisponivelException | ProvedorRecusouException | ProvedorNaoConfiguradoException e) {
            log.warn("Chave PIX da conta {} nao conferida: {}", idContaExterna, e.getClass().getSimpleName());
        }
    }

    // ---- Resposta ----

    private ContaRecebimentoResponse resposta(ContaRecebimentoEscola conta, Escola escola,
            HabilitacaoRecebimentos habilitacao) {
        SituacaoContaResponse situacao = conta.getSituacaoGeral() == null ? null
                : new SituacaoContaResponse(nome(conta.getSituacaoGeral()), nome(conta.getSituacaoDocumentacao()),
                        nome(conta.getSituacaoDadosComerciais()), nome(conta.getSituacaoContaBancaria()));
        return new ContaRecebimentoResponse(conta.getStatus().name(), habilitacao.habilitados(),
                habilitacao.motivo(), conta.getRazaoSocial(), conta.getEmailFinanceiro(),
                conta.getTelefoneCelular(), conta.getTipoEmpresa(), conta.getFaturamentoMensal(),
                conta.getLogradouro(), conta.getNumero(), conta.getComplemento(), conta.getBairro(), conta.getCep(),
                escola.getCnpjCpf(), conta.getBancoCodigo(), conta.getContaFinal(), situacao, conta.getUltimoErro(),
                conta.getSolicitadaEm(), conta.getAprovadaEm(), conta.getSincronizadaEm(),
                ATIVAVEIS.contains(conta.getStatus()),
                SINCRONIZAVEIS.contains(conta.getStatus()) && conta.getCredencialCifrada() != null);
    }

    private static ContaRecebimentoResponse naoIniciada(Escola escola, HabilitacaoRecebimentos habilitacao) {
        return new ContaRecebimentoResponse("NAO_INICIADA", habilitacao.habilitados(), habilitacao.motivo(), null,
                null, null, null, null, null, null, null, null, null, escola.getCnpjCpf(), null, null, null, null,
                null, null, null, true, false);
    }

    // ---- Utilitarios ----

    /** A recusa ja vem saneada pelo adaptador; as outras falhas tem mensagem fixa. */
    private static String mensagemDaFalha(RuntimeException e) {
        String mensagem = e instanceof ProvedorRecusouException && e.getMessage() != null
                ? e.getMessage()
                : "Provedor de pagamento nao configurado";
        return mensagem.length() <= TAMANHO_MAXIMO_ERRO ? mensagem : mensagem.substring(0, TAMANHO_MAXIMO_ERRO);
    }

    private static String nome(SituacaoCadastral situacao) {
        return situacao == null ? null : situacao.name();
    }

    private static String digitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    /** CONTA_FINAL e CHAR(4): conta com menos de 4 digitos e completada com zeros a esquerda. */
    static String quatroUltimos(String conta) {
        String preenchida = "0000" + conta;
        return preenchida.substring(preenchida.length() - 4);
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /**
     * O que o passo 1 gravou e os passos seguintes usam fora da transacao.
     * {@code novaConta} leva os dados bancarios so em memoria, ate o provedor.
     *
     * @param procurarAntes a ultima tentativa ficou inconclusiva: buscar pelo CNPJ antes de criar
     */
    private record Reserva(UUID contaId, String cnpj, boolean procurarAntes, DadosNovaConta novaConta) {
    }
}
