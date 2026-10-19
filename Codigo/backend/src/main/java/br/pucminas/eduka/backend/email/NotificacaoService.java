package br.pucminas.eduka.backend.email;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.pucminas.eduka.backend.domain.Notificacao;
import br.pucminas.eduka.backend.domain.StatusNotificacao;
import br.pucminas.eduka.backend.domain.TipoNotificacao;
import br.pucminas.eduka.backend.repository.NotificacaoRepository;

/**
 * Porta de entrada do envio de e-mail para o resto do sistema. Quem monta o
 * e-mail (os listeners de evento) so chama {@link #enfileirar}: nao sabe de
 * provedor, falha nem nova tentativa.
 *
 * <p>
 * A notificacao e gravada na transacao de quem chamou: se a operacao de
 * negocio der rollback, ela some junto, e nada sai. Logo depois do commit
 * ha uma tentativa imediata (o codigo de login nao pode esperar a rotina);
 * se falhar, {@link NotificacaoRotina} tenta de novo.
 * </p>
 */
@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoProcessador processador;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository notificacaoRepository, NotificacaoProcessador processador,
            Clock clock) {
        this.notificacaoRepository = notificacaoRepository;
        this.processador = processador;
        this.clock = clock;
    }

    /**
     * Poe o e-mail na fila e agenda a tentativa imediata para depois do
     * commit.
     *
     * @param referenciaId registro que originou o aviso (usuario, cobranca)
     * @param validaAte    depois disso o e-mail nao sai mais; nulo nao expira
     */
    @Transactional
    public Notificacao enfileirar(TipoNotificacao tipo, UUID referenciaId, Email email, Instant validaAte) {
        Instant agora = clock.instant();
        Notificacao notificacao = notificacaoRepository.save(Notificacao.builder()
                .tipo(tipo)
                .referenciaId(referenciaId)
                .destinatario(email.destinatario())
                .nomeDestinatario(email.nomeDestinatario())
                .assunto(email.assunto())
                .html(email.html())
                .texto(email.texto())
                .status(StatusNotificacao.PENDENTE)
                .tentativas(0)
                .proximaTentativaEm(agora)
                .validaAte(validaAte)
                .criadoEm(agora)
                .build());

        UUID id = notificacao.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enviarAgora(id);
            }
        });
        return notificacao;
    }

    /** Falha aqui nao chega a quem chamou: a notificacao continua na fila para a rotina. */
    private void enviarAgora(UUID id) {
        try {
            processador.processar(id);
        } catch (RuntimeException ex) {
            log.error("Tentativa imediata da notificacao {} falhou; fica para a rotina ({})", id,
                    ex.getClass().getSimpleName());
        }
    }
}
