package br.pucminas.eduka.backend.service;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.EmailSender;

/**
 * Manda o e-mail de primeiro acesso so depois do commit: se o cadastro der
 * rollback, ninguem recebe credencial de um usuario que nao existe.
 *
 * <p>
 * Falha no envio nao desfaz nada (o commit ja aconteceu) e nao chega ao
 * cliente: fica no log, e a escola pode pedir o reenvio.
 * </p>
 */
@Component
public class AcessoInicialEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AcessoInicialEmailListener.class);

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    static final String ASSUNTO = "Seu acesso ao Eduka";

    private final EmailSender emailSender;
    private final String urlLogin;

    public AcessoInicialEmailListener(EmailSender emailSender, @Value("${app.frontend-url}") String frontendUrl) {
        this.emailSender = emailSender;
        this.urlLogin = frontendUrl.replaceAll("/+$", "") + "/login";
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void enviar(AcessoInicialCriado acesso) {
        try {
            emailSender.enviar(montarEmail(acesso));
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar e-mail de primeiro acesso do usuario {}", acesso.usuarioId(), ex);
        }
    }

    Email montarEmail(AcessoInicialCriado acesso) {
        String validade = DATA.format(acesso.expiraEm());
        String texto = """
                Olá, %s!

                Seu acesso ao Eduka foi criado.

                Login: %s
                Senha provisória: seu CPF, somente os números.

                No primeiro acesso você vai definir uma senha definitiva.
                A senha provisória vale até %s.

                Acesse: %s

                Se você não esperava este e-mail, ignore-o.
                """.formatted(acesso.nome(), acesso.login(), validade, urlLogin);
        String html = """
                <p>Olá, %s!</p>
                <p>Seu acesso ao Eduka foi criado.</p>
                <p><strong>Login:</strong> %s<br>
                <strong>Senha provisória:</strong> seu CPF, somente os números.</p>
                <p>No primeiro acesso você vai definir uma senha definitiva.
                A senha provisória vale até %s.</p>
                <p><a href="%s">Acessar o Eduka</a></p>
                <p>Se você não esperava este e-mail, ignore-o.</p>
                """.formatted(htmlEscape(acesso.nome()), htmlEscape(acesso.login()), validade,
                htmlEscape(urlLogin));
        return new Email(acesso.email(), acesso.nome(), ASSUNTO, html, texto);
    }
}
