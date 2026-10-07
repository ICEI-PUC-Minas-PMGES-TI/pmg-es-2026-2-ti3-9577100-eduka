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

    /**
     * Servido pelo front ({@code frontend/public/email}). PNG e nao SVG: Gmail
     * e Outlook nao exibem SVG. Em dev ({@code localhost}) o cliente de e-mail
     * nao alcanca a imagem e mostra o texto alternativo no lugar.
     */
    static final String CAMINHO_LOGO = "/email/eduka-logo-branca.png";

    private final EmailSender emailSender;
    private final String urlLogin;
    private final String urlLogo;

    public AcessoInicialEmailListener(EmailSender emailSender, @Value("${app.frontend-url}") String frontendUrl) {
        this.emailSender = emailSender;
        String base = frontendUrl.replaceAll("/+$", "");
        this.urlLogin = base + "/login";
        this.urlLogo = base + CAMINHO_LOGO;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void enviar(AcessoInicialCriado acesso) {
        try {
            emailSender.enviar(montarEmail(acesso));
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar e-mail de primeiro acesso do usuario {}", acesso.usuarioId(), ex);
        }
    }

    /**
     * HTML de e-mail: layout em tabelas e estilo inline, porque Gmail e
     * Outlook ignoram CSS externo e boa parte de {@code <style>}. Todo
     * {@code %} literal e {@code %%} por causa do {@code formatted}.
     */
    Email montarEmail(AcessoInicialCriado acesso) {
        String validade = DATA.format(acesso.expiraEm());
        String texto = """
                Olá, %s!

                Seu acesso ao Eduka foi criado. Use os dados abaixo para entrar pela primeira vez.

                Login: %s
                Senha provisória: seu CPF, somente os números

                No primeiro acesso você vai criar uma senha definitiva.
                A senha provisória vale até %s. Depois disso, peça à secretaria da escola um novo envio.

                Acesse: %s

                Se você não esperava este e-mail, pode ignorá-lo. Ninguém da equipe Eduka vai pedir sua senha.
                Este é um e-mail automático, não responda.
                """.formatted(acesso.nome(), acesso.login(), validade, urlLogin);
        String html = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Seu acesso ao Eduka</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f8f3ff;">
                <div style="display:none; max-height:0; overflow:hidden; opacity:0;">
                  Seu acesso foi criado. Entre com seu e-mail e defina sua senha até %3$s.
                </div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="background-color:#f8f3ff;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                             style="max-width:560px; background-color:#ffffff; border-radius:12px;
                                    font-family:Arial, Helvetica, sans-serif; color:#3d3550;">
                        <tr>
                          <td style="background-color:#6a3fc4; border-radius:12px 12px 0 0; padding:24px 32px;">
                            <img src="%5$s" width="152" height="40" alt="Eduka"
                                 style="display:block; border:0; outline:none; text-decoration:none;
                                        font-size:24px; font-weight:bold; color:#ffffff;">
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px 32px 8px 32px;">
                            <h1 style="margin:0 0 16px 0; font-size:20px; line-height:28px; color:#3d3550;">
                              Olá, %1$s!
                            </h1>
                            <p style="margin:0 0 24px 0; font-size:15px; line-height:24px; color:#5f5873;">
                              Seu acesso ao Eduka foi criado. Use os dados abaixo para entrar pela primeira vez.
                            </p>
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                                   style="background-color:#f8f3ff; border-radius:8px;">
                              <tr>
                                <td style="padding:16px 20px; font-size:15px; line-height:24px; color:#3d3550;">
                                  <strong>Login:</strong> %2$s<br>
                                  <strong>Senha provisória:</strong> seu CPF, somente os números
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td align="center" style="padding:24px 32px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                              <tr>
                                <td align="center" bgcolor="#6a3fc4" style="border-radius:8px;">
                                  <a href="%4$s" target="_blank"
                                     style="display:inline-block; padding:14px 32px; font-size:16px; font-weight:bold;
                                            color:#ffffff; text-decoration:none; border-radius:8px;">
                                    Acessar o Eduka
                                  </a>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:0 32px 24px 32px; font-size:14px; line-height:22px; color:#5f5873;">
                            <p style="margin:0 0 12px 0;">
                              No primeiro acesso você vai criar uma senha definitiva.
                              A senha provisória vale até <strong style="color:#3d3550;">%3$s</strong>.
                              Depois disso, peça à secretaria da escola um novo envio.
                            </p>
                            <p style="margin:0; font-size:13px;">
                              Se o botão não funcionar, copie e cole este endereço no navegador:<br>
                              <a href="%4$s" style="color:#6a3fc4; word-break:break-all;">%4$s</a>
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="border-top:1px solid #ece4fa; padding:20px 32px; font-size:12px;
                                     line-height:18px; color:#8a829c;">
                            Se você não esperava este e-mail, pode ignorá-lo. Ninguém da equipe Eduka vai pedir sua senha.<br>
                            Este é um e-mail automático, não responda.
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(htmlEscape(acesso.nome()), htmlEscape(acesso.login()), validade,
                htmlEscape(urlLogin), htmlEscape(urlLogo));
        return new Email(acesso.email(), acesso.nome(), ASSUNTO, html, texto);
    }
}
