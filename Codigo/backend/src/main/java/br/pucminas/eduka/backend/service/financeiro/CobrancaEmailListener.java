package br.pucminas.eduka.backend.service.financeiro;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import br.pucminas.eduka.backend.domain.notificacao.TipoNotificacao;
import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.email.NotificacaoService;
import br.pucminas.eduka.backend.service.acesso.AcessoInicialEmailListener;

/**
 * Monta os e-mails de cobranca (criada, vencimento proximo, pagamento
 * confirmado) e os poe na fila de notificacoes, na transacao da operacao: se
 * ela der rollback, ninguem recebe aviso de cobranca que nao existe. Envio e
 * novas tentativas ficam com {@link NotificacaoService}.
 *
 * <p>
 * O lembrete de vencimento deixa de sair quando o vencimento passa.
 * </p>
 */
@Component
public class CobrancaEmailListener {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(FUSO_ESCOLA);

    private final NotificacaoService notificacaoService;
    private final String urlLogin;
    private final String urlLogo;

    public CobrancaEmailListener(NotificacaoService notificacaoService,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.notificacaoService = notificacaoService;
        String base = frontendUrl.replaceAll("/+$", "");
        this.urlLogin = base + "/login";
        this.urlLogo = base + AcessoInicialEmailListener.CAMINHO_LOGO;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void enviar(AvisoCobranca aviso) {
        notificacaoService.enfileirar(tipoDe(aviso), aviso.cobrancaId(), montarEmail(aviso), validaAte(aviso));
    }

    private static TipoNotificacao tipoDe(AvisoCobranca aviso) {
        return switch (aviso.tipo()) {
            case COBRANCA_CRIADA -> TipoNotificacao.COBRANCA_CRIADA;
            case VENCIMENTO_PROXIMO -> TipoNotificacao.VENCIMENTO_PROXIMO;
            case PAGAMENTO_CONFIRMADO -> TipoNotificacao.PAGAMENTO_CONFIRMADO;
        };
    }

    /** Lembrete de vencimento so faz sentido ate o fim do dia do vencimento, no fuso da escola. */
    private static Instant validaAte(AvisoCobranca aviso) {
        return aviso.tipo() == AvisoCobranca.Tipo.VENCIMENTO_PROXIMO
                ? aviso.dataVencimento().plusDays(1).atStartOfDay(FUSO_ESCOLA).toInstant()
                : null;
    }

    Email montarEmail(AvisoCobranca aviso) {
        String vencimento = DATA.format(aviso.dataVencimento());
        String deQuem = aviso.destinatarioEhAluno() || aviso.alunoNome() == null ? ""
                : " de " + aviso.alunoNome();
        String escola = aviso.escolaNome() == null ? "a escola" : aviso.escolaNome();

        String assunto;
        String titulo;
        String mensagem;
        String resumo;
        String fechamento;
        List<String[]> detalhes = new ArrayList<>();
        switch (aviso.tipo()) {
            case COBRANCA_CRIADA -> {
                assunto = "Nova cobrança: " + aviso.descricao();
                titulo = "Nova cobrança";
                mensagem = "%s lançou uma nova cobrança%s.".formatted(maiuscula(escola), deQuem);
                resumo = "Nova cobrança de %s com vencimento em %s.".formatted(moeda(aviso.valor()), vencimento);
                fechamento = "Você pode acompanhar suas cobranças no Eduka.";
            }
            case VENCIMENTO_PROXIMO -> {
                assunto = "Lembrete: cobrança vence em " + vencimento;
                titulo = "Cobrança perto do vencimento";
                mensagem = "Lembrete: a cobrança%s abaixo vence em %s.".formatted(deQuem, vencimento);
                resumo = "%s vence em %s.".formatted(moeda(aviso.valor()), vencimento);
                fechamento = "Se o pagamento já foi feito, desconsidere este lembrete.";
            }
            case PAGAMENTO_CONFIRMADO -> {
                assunto = "Pagamento confirmado: " + aviso.descricao();
                titulo = "Pagamento confirmado";
                mensagem = "%s confirmou o pagamento da cobrança%s abaixo. Obrigado!".formatted(maiuscula(escola),
                        deQuem);
                resumo = "Pagamento de %s confirmado.".formatted(moeda(aviso.valor()));
                fechamento = "Guarde este e-mail como comprovante da baixa.";
            }
            default -> throw new IllegalStateException("Tipo de aviso sem e-mail: " + aviso.tipo());
        }
        if (aviso.alunoNome() != null) {
            detalhes.add(new String[] { "Aluno", aviso.alunoNome() });
        }
        detalhes.add(new String[] { "Descrição", aviso.descricao() });
        detalhes.add(new String[] { "Valor", moeda(aviso.valor()) });
        detalhes.add(new String[] { "Vencimento", vencimento });
        if (aviso.pagaEm() != null) {
            detalhes.add(new String[] { "Pago em", DATA_HORA.format(aviso.pagaEm()) });
        }

        String texto = """
                Olá, %s!

                %s

                %s

                %s
                Acesse: %s

                Este é um e-mail automático, não responda. Dúvidas sobre a cobrança: fale com %s.
                """.formatted(aviso.destinatarioNome(), mensagem,
                detalhes.stream().map(d -> d[0] + ": " + d[1]).collect(Collectors.joining("\n")), fechamento,
                urlLogin, escola);
        String html = html(titulo, resumo, aviso.destinatarioNome(), mensagem, detalhes, fechamento, escola);
        return new Email(aviso.destinatarioEmail(), aviso.destinatarioNome(), assunto, html, texto);
    }

    /**
     * Mesmo layout do e-mail de primeiro acesso: tabelas e estilo inline,
     * porque Gmail e Outlook ignoram CSS externo. Todo {@code %} literal e
     * {@code %%} por causa do {@code formatted}.
     */
    private String html(String titulo, String resumo, String nome, String mensagem, List<String[]> detalhes,
            String fechamento, String escola) {
        String linhas = detalhes.stream()
                .map(d -> "<strong>%s:</strong> %s".formatted(escapar(d[0]), escapar(d[1])))
                .collect(Collectors.joining("<br>\n"));
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%1$s</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f8f3ff;">
                <div style="display:none; max-height:0; overflow:hidden; opacity:0;">%2$s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="background-color:#f8f3ff;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                             style="max-width:560px; background-color:#ffffff; border-radius:12px;
                                    font-family:Arial, Helvetica, sans-serif; color:#3d3550;">
                        <tr>
                          <td style="background-color:#6a3fc4; border-radius:12px 12px 0 0; padding:24px 32px;">
                            <img src="%3$s" width="152" height="40" alt="Eduka"
                                 style="display:block; border:0; outline:none; text-decoration:none;
                                        font-size:24px; font-weight:bold; color:#ffffff;">
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px 32px 8px 32px;">
                            <h1 style="margin:0 0 16px 0; font-size:20px; line-height:28px; color:#3d3550;">
                              Olá, %4$s!
                            </h1>
                            <p style="margin:0 0 24px 0; font-size:15px; line-height:24px; color:#5f5873;">
                              %5$s
                            </p>
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                                   style="background-color:#f8f3ff; border-radius:8px;">
                              <tr>
                                <td style="padding:16px 20px; font-size:15px; line-height:24px; color:#3d3550;">
                                  %6$s
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
                                  <a href="%7$s" target="_blank"
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
                            <p style="margin:0;">%8$s</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="border-top:1px solid #ece4fa; padding:20px 32px; font-size:12px;
                                     line-height:18px; color:#8a829c;">
                            Este é um e-mail automático, não responda. Dúvidas sobre a cobrança: fale com %9$s.
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(escapar(titulo), escapar(resumo), escapar(urlLogo), escapar(nome),
                escapar(mensagem), linhas, escapar(urlLogin), escapar(fechamento), escapar(escola));
    }

    /** So os caracteres especiais de HTML: o e-mail ja e UTF-8, acento fica como esta. */
    private static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto, "UTF-8");
    }

    private static String maiuscula(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** NumberFormat nao e thread-safe: um por chamada. */
    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor);
    }
}
