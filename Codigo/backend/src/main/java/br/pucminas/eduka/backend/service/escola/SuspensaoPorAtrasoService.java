package br.pucminas.eduka.backend.service.escola;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.escola.MotivoSuspensao;
import br.pucminas.eduka.backend.domain.notificacao.TipoNotificacao;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.escola.SuspensaoPrevistaResponse;
import br.pucminas.eduka.backend.email.NotificacaoService;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.escola.SuspensaoPorAtrasoRepository.FaturaEmAtraso;
import br.pucminas.eduka.backend.repository.escola.SuspensaoPorAtrasoRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Suspensao automatica da escola por fatura da plataforma em atraso (RF24).
 *
 * <ol>
 * <li>Fatura em aberto vencida ha {@code aviso-apos-dias}: o MASTER recebe o
 * aviso de que a escola sera suspensa em {@code dias-ate-suspender} dias, e a
 * data prevista fica gravada na fatura.</li>
 * <li>Chegada a data com a fatura ainda em aberto: a escola e suspensa
 * ({@link MotivoSuspensao#FATURA_EM_ATRASO}) e o MASTER recebe outro e-mail.
 * Ninguem da escola entra ate o Super Admin reativar.</li>
 * </ol>
 *
 * <p>
 * Paga ou cancelada, a fatura sai das duas etapas. Reativar a escola zera os
 * avisos das faturas em aberto ({@link EscolaRepository#reiniciarAvisosDeSuspensao}): o ciclo recomeca, e
 * a escola nao e suspensa de novo sem um novo aviso.
 * </p>
 *
 * <p>
 * Cada fatura e tratada na propria transacao: uma falha nao para as demais, e
 * a proxima execucao tenta de novo.
 * </p>
 */
@Service
@EnableConfigurationProperties(SuspensaoPorAtrasoProperties.class)
public class SuspensaoPorAtrasoService {

    private static final Logger log = LoggerFactory.getLogger(SuspensaoPorAtrasoService.class);
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final SuspensaoPorAtrasoRepository repository;
    private final EscolaRepository escolaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final NotificacaoService notificacaoService;
    private final SuspensaoEmail suspensaoEmail;
    private final SuspensaoPorAtrasoProperties prazos;
    private final TransactionTemplate transacao;
    private final Clock clock;
    private final AuditoriaService auditoriaService;

    public SuspensaoPorAtrasoService(SuspensaoPorAtrasoRepository repository, EscolaRepository escolaRepository,
            UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            NotificacaoService notificacaoService, SuspensaoEmail suspensaoEmail, SuspensaoPorAtrasoProperties prazos,
            PlatformTransactionManager transactionManager, Clock clock, AuditoriaService auditoriaService) {
        this.repository = repository;
        this.escolaRepository = escolaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.notificacaoService = notificacaoService;
        this.suspensaoEmail = suspensaoEmail;
        this.prazos = prazos;
        this.transacao = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.auditoriaService = auditoriaService;
    }

    /** @return quantas faturas receberam o aviso */
    public int avisar() {
        LocalDate hoje = hoje();
        LocalDate prevista = hoje.plusDays(prazos.diasAteSuspender());
        int avisadas = 0;
        for (FaturaEmAtraso fatura : repository.paraAvisar(hoje.minusDays(prazos.avisoAposDias()))) {
            try {
                Boolean avisou = transacao.execute(status -> {
                    if (repository.marcarAviso(fatura.getId(), clock.instant(), prevista) != 1) {
                        return false;
                    }
                    enviarAosMasters(fatura, TipoNotificacao.AVISO_SUSPENSAO, prevista);
                    return true;
                });
                if (Boolean.TRUE.equals(avisou)) {
                    avisadas++;
                }
            } catch (RuntimeException e) {
                log.warn("Nao foi possivel avisar a suspensao da fatura {}: {}", fatura.getId(),
                        e.getClass().getSimpleName());
            }
        }
        return avisadas;
    }

    /** @return quantas escolas foram suspensas */
    public int suspender() {
        Set<UUID> suspensas = new HashSet<>();
        for (FaturaEmAtraso fatura : repository.paraSuspender(hoje())) {
            if (suspensas.contains(fatura.getEscolaId())) {
                continue;
            }
            try {
                Boolean suspendeu = transacao.execute(status -> {
                    Escola escola = escolaRepository.findById(fatura.getEscolaId()).orElse(null);
                    if (escola == null || !escola.isAtivo()) {
                        return false;
                    }
                    escola.suspender(MotivoSuspensao.FATURA_EM_ATRASO);
                    escolaRepository.save(escola);
                    // Ninguem suspendeu: foi a rotina, pela fatura em atraso.
                    auditoriaService.registrarDoSistema(OperacaoAuditoria.SUSPENDER, EntidadeAuditada.ESCOLA,
                            escola.getId(), escola.getId());
                    enviarAosMasters(fatura, TipoNotificacao.ESCOLA_SUSPENSA, fatura.getSuspensaoPrevistaPara());
                    return true;
                });
                if (Boolean.TRUE.equals(suspendeu)) {
                    suspensas.add(fatura.getEscolaId());
                    log.info("Escola {} suspensa pela fatura {} em atraso", fatura.getEscolaId(), fatura.getId());
                }
            } catch (RuntimeException e) {
                log.warn("Nao foi possivel suspender a escola {} pela fatura {}: {}", fatura.getEscolaId(),
                        fatura.getId(), e.getClass().getSimpleName());
            }
        }
        return suspensas.size();
    }

    /** O aviso que o MASTER ve na tela enquanto a suspensao esta prevista. */
    public Optional<SuspensaoPrevistaResponse> prevista(UUID escolaId) {
        LocalDate hoje = hoje();
        return repository.suspensaoPrevista(escolaId)
                .map(f -> new SuspensaoPrevistaResponse(f.getSuspensaoPrevistaPara(),
                        Math.max(0, ChronoUnit.DAYS.between(hoje, f.getSuspensaoPrevistaPara())), f.getId(),
                        f.getDescricao(), f.getValor(), f.getDataVencimento()));
    }

    /**
     * Os MASTER ativos da escola com e-mail; sem nenhum, o e-mail de contato da
     * escola. Sem e-mail nenhum, fica so no log (sem dado pessoal).
     */
    private void enviarAosMasters(FaturaEmAtraso fatura, TipoNotificacao tipo, LocalDate suspensaoEm) {
        Escola escola = escolaRepository.findById(fatura.getEscolaId()).orElse(null);
        String nomeEscola = escola == null ? null : escola.getNome();
        List<UUID> pessoas = usuarioRepository.findAllByEscolaIdAndPerfil(fatura.getEscolaId(), PerfilUsuario.MASTER)
                .stream()
                .filter(Usuario::isAtivo)
                .map(Usuario::getPessoaId)
                .toList();
        List<Pessoa> comEmail = pessoas.isEmpty() ? List.of()
                : pessoaRepository.findAllByIdInAndEscolaId(pessoas, fatura.getEscolaId()).stream()
                        .filter(p -> p.getEmail() != null && !p.getEmail().isBlank())
                        .toList();
        if (comEmail.isEmpty()) {
            if (escola != null && escola.getEmail() != null && !escola.getEmail().isBlank()) {
                notificacaoService.enfileirar(tipo, fatura.getId(), suspensaoEmail.montar(tipo,
                        escola.getEmail().trim(), "equipe da " + nomeEscola, nomeEscola, fatura, suspensaoEm), null);
            } else {
                log.warn("Aviso {} da fatura {} sem MASTER com e-mail; nao enviado", tipo, fatura.getId());
            }
            return;
        }
        for (Pessoa master : comEmail) {
            notificacaoService.enfileirar(tipo, fatura.getId(), suspensaoEmail.montar(tipo, master.getEmail().trim(),
                    master.getNome(), nomeEscola, fatura, suspensaoEm), null);
        }
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }
}
