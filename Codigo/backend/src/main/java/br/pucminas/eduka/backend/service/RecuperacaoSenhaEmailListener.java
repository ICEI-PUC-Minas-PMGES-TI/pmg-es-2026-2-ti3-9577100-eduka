package br.pucminas.eduka.backend.service;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.pucminas.eduka.backend.domain.TipoNotificacao;
import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.NotificacaoService;

/**
 * Monta o e-mail com o codigo de recuperacao de senha e o poe na fila de
 * notificacoes, na transacao que gravou o codigo, como em
 * {@link CodigoLoginEmailListener}. Codigo vencido nao e enviado.
 */
@Component
public class RecuperacaoSenhaEmailListener {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaEmailListener.class);

    static final String ASSUNTO = "Redefinição de senha do Eduka";

    private final NotificacaoService notificacaoService;
    private final RecuperacaoSenhaProperties properties;
    private final Clock clock;
    private final String urlLogo;

    public RecuperacaoSenhaEmailListener(NotificacaoService notificacaoService,
            RecuperacaoSenhaProperties properties, Clock clock, @Value("${app.frontend-url}") String frontendUrl) {
        this.notificacaoService = notificacaoService;
        this.properties = properties;
        this.clock = clock;
        this.urlLogo = frontendUrl.replaceAll("/+$", "") + AcessoInicialEmailListener.CAMINHO_LOGO;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void enviar(CodigoRecuperacaoSenhaGerado gerado) {
        if (properties.codigoNoLog()) {
            // So em dev sem Brevo (RECUPERACAO_SENHA_CODIGO_NO_LOG): sem isto nao ha como ler o codigo.
            log.info("Codigo de recuperacao de senha do usuario {}: {}", gerado.usuarioId(), gerado.codigo());
        }
        Instant validaAte = clock.instant().plus(Duration.ofMinutes(properties.validadeMinutos()));
        notificacaoService.enfileirar(TipoNotificacao.RECUPERACAO_SENHA, gerado.usuarioId(), montarEmail(gerado),
                validaAte);
    }

    /**
     * HTML de e-mail: layout em tabelas e estilo inline, como em
     * {@link CodigoLoginEmailListener}. Todo {@code %} literal e {@code %%}
     * por causa do {@code formatted}.
     */
    Email montarEmail(CodigoRecuperacaoSenhaGerado gerado) {
        long minutos = properties.validadeMinutos();
        String validade = minutos == 1 ? "1 minuto" : minutos + " minutos";
        String texto = """
                Olá, %s!

                Recebemos um pedido para redefinir a senha da sua conta no Eduka.
                Use o código abaixo na tela "Esqueceu sua senha?" para criar uma senha nova:

                %s

                O código vale por %s e só pode ser usado uma vez.

                Se não foi você quem pediu, ignore este e-mail: sua senha continua a mesma.
                Ninguém da equipe Eduka vai pedir este código.
                Este é um e-mail automático, não responda.
                """.formatted(gerado.nome(), gerado.codigo(), validade);
        String html = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Redefinição de senha do Eduka</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f8f3ff;">
                <div style="display:none; max-height:0; overflow:hidden; opacity:0;">
                  Seu código para redefinir a senha vale por %3$s.
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
                            <img src="%4$s" width="152" height="40" alt="Eduka"
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
                              Recebemos um pedido para redefinir a senha da sua conta no Eduka. Use o código
                              abaixo na tela <strong style="color:#3d3550;">Esqueceu sua senha?</strong>
                              para criar uma senha nova.
                            </p>
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                                   style="background-color:#f8f3ff; border-radius:8px;">
                              <tr>
                                <td align="center"
                                    style="padding:20px; font-family:'Courier New', Courier, monospace;
                                           font-size:34px; line-height:40px; font-weight:bold;
                                           letter-spacing:10px; color:#6a3fc4;">
                                  %2$s
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 32px 24px 32px; font-size:14px; line-height:22px; color:#5f5873;">
                            O código vale por <strong style="color:#3d3550;">%3$s</strong>
                            e só pode ser usado uma vez.
                          </td>
                        </tr>
                        <tr>
                          <td style="border-top:1px solid #ece4fa; padding:20px 32px; font-size:12px;
                                     line-height:18px; color:#8a829c;">
                            Se não foi você quem pediu, ignore este e-mail: sua senha continua a mesma.
                            Ninguém da equipe Eduka vai pedir este código.<br>
                            Este é um e-mail automático, não responda.
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(htmlEscape(gerado.nome()), gerado.codigo(), validade, htmlEscape(urlLogo));
        return new Email(gerado.email(), gerado.nome(), ASSUNTO, html, texto);
    }
}
