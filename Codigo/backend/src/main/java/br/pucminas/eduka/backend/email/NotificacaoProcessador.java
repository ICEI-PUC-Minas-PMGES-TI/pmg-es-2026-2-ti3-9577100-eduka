package br.pucminas.eduka.backend.email;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import br.pucminas.eduka.backend.domain.notificacao.Notificacao;
import br.pucminas.eduka.backend.domain.notificacao.StatusNotificacao;
import br.pucminas.eduka.backend.repository.notificacao.NotificacaoRepository;

/**
 * Faz uma tentativa de envio de uma notificacao: trava a linha, envia pelo
 * {@link EmailSender} e grava o resultado.
 *
 * <ul>
 * <li>Enviou: ENVIADA.</li>
 * <li>Provedor recusou de vez ({@link EmailRecusadoException}): FALHOU.</li>
 * <li>Outra falha: nova tentativa depois de
 * {@link NotificacaoProperties#esperaApos}; esgotadas, FALHOU.</li>
 * <li>Passou de {@code validaAte}: EXPIRADA, sem enviar.</li>
 * </ul>
 *
 * <p>
 * Cada chamada roda na propria transacao ({@code REQUIRES_NEW}): e chamada
 * depois do commit de outra e pela rotina, e o resultado de uma notificacao
 * nao pode depender do de outra. A trava com {@code SKIP LOCKED} garante que
 * a tentativa imediata e a rotina nunca enviam a mesma notificacao duas
 * vezes.
 * </p>
 *
 * <p>
 * Log e {@code ULTIMO_ERRO} levam so o tipo do erro e o status HTTP: a
 * mensagem do provedor pode ecoar o destinatario.
 * </p>
 */
@Component
public class NotificacaoProcessador {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoProcessador.class);

    private final NotificacaoRepository notificacaoRepository;
    private final EmailSender emailSender;
    private final NotificacaoProperties properties;
    private final Clock clock;

    public NotificacaoProcessador(NotificacaoRepository notificacaoRepository, EmailSender emailSender,
            NotificacaoProperties properties, Clock clock) {
        this.notificacaoRepository = notificacaoRepository;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Tenta enviar, se a notificacao ainda estiver pendente e na hora. Travada
     * por outra execucao, ja finalizada ou fora da hora: nao faz nada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processar(UUID id) {
        Notificacao notificacao = notificacaoRepository.travarParaEnvio(id).orElse(null);
        Instant agora = clock.instant();
        if (notificacao == null || notificacao.getStatus() != StatusNotificacao.PENDENTE
                || notificacao.getProximaTentativaEm().isAfter(agora)) {
            return;
        }
        if (notificacao.getValidaAte() != null && agora.isAfter(notificacao.getValidaAte())) {
            notificacao.finalizar(StatusNotificacao.EXPIRADA, agora);
            log.warn("Notificacao {} ({}) expirou sem ser enviada", id, notificacao.getTipo());
            return;
        }

        notificacao.setTentativas(notificacao.getTentativas() + 1);
        try {
            emailSender.enviar(new Email(notificacao.getDestinatario(), notificacao.getNomeDestinatario(),
                    notificacao.getAssunto(), notificacao.getHtml(), notificacao.getTexto()));
            notificacao.setUltimoErro(null);
            notificacao.finalizar(StatusNotificacao.ENVIADA, agora);
        } catch (EmailRecusadoException ex) {
            notificacao.setUltimoErro(descrever(ex));
            notificacao.finalizar(StatusNotificacao.FALHOU, agora);
            log.error("Notificacao {} ({}) recusada pelo provedor: {}", id, notificacao.getTipo(),
                    notificacao.getUltimoErro());
        } catch (RuntimeException ex) {
            registrarFalhaTemporaria(notificacao, ex, agora);
        }
    }

    private void registrarFalhaTemporaria(Notificacao notificacao, RuntimeException ex, Instant agora) {
        notificacao.setUltimoErro(descrever(ex));
        if (notificacao.getTentativas() >= properties.maxTentativas()) {
            notificacao.finalizar(StatusNotificacao.FALHOU, agora);
            log.error("Notificacao {} ({}) falhou de vez apos {} tentativa(s): {}", notificacao.getId(),
                    notificacao.getTipo(), notificacao.getTentativas(), notificacao.getUltimoErro());
            return;
        }
        notificacao.setProximaTentativaEm(agora.plus(properties.esperaApos(notificacao.getTentativas())));
        notificacao.setAtualizadoEm(agora);
        log.warn("Notificacao {} ({}) falhou na tentativa {}: {}; nova tentativa as {}", notificacao.getId(),
                notificacao.getTipo(), notificacao.getTentativas(), notificacao.getUltimoErro(),
                notificacao.getProximaTentativaEm());
    }

    /** Tipo do erro e, se houver, o status HTTP. Nunca a mensagem. */
    static String descrever(RuntimeException ex) {
        String tipo = ex.getClass().getSimpleName();
        if (ex instanceof EmailRecusadoException recusado) {
            return tipo + " (HTTP " + recusado.status() + ")";
        }
        if (ex instanceof RestClientResponseException resposta) {
            return tipo + " (HTTP " + resposta.getStatusCode().value() + ")";
        }
        return tipo;
    }
}
