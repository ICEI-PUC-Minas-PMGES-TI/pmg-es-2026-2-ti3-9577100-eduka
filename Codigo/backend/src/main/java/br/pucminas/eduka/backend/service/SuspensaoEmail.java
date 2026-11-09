package br.pucminas.eduka.backend.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import br.pucminas.eduka.backend.domain.TipoNotificacao;
import br.pucminas.eduka.backend.email.Email;
import br.pucminas.eduka.backend.repository.SuspensaoPorAtrasoRepository.FaturaEmAtraso;

/**
 * E-mails ao MASTER sobre a suspensao por fatura em atraso: o aviso (a escola
 * sera suspensa em tal dia) e a suspensao feita. Mesmo layout dos e-mails de
 * cobranca, com estilo inline para Gmail e Outlook.
 */
@Component
public class SuspensaoEmail {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String urlLogin;
    private final String urlLogo;

    public SuspensaoEmail(@Value("${app.frontend-url}") String frontendUrl) {
        String base = frontendUrl.replaceAll("/+$", "");
        this.urlLogin = base + "/login";
        this.urlLogo = base + AcessoInicialEmailListener.CAMINHO_LOGO;
    }

    Email montar(TipoNotificacao tipo, String destinatario, String nome, String escolaNome, FaturaEmAtraso fatura,
            LocalDate suspensaoEm) {
        String escola = escolaNome == null ? "sua escola" : escolaNome;
        String vencimento = DATA.format(fatura.getDataVencimento());
        String dia = suspensaoEm == null ? null : DATA.format(suspensaoEm);
        String assunto;
        String titulo;
        String mensagem;
        String fechamento;
        String botao;
        if (tipo == TipoNotificacao.AVISO_SUSPENSAO) {
            assunto = "Aviso: o acesso de " + escola + " será suspenso em " + dia;
            titulo = "Fatura do Eduka em atraso";
            mensagem = ("A fatura do Eduka de %s, vencida em %s, ainda está em aberto. Se ela não for paga até %s, "
                    + "o acesso de toda a equipe e dos alunos de %s ao Eduka será suspenso.")
                    .formatted(escola, vencimento, dia, escola);
            fechamento = "Pague pelo Eduka em Recebimentos > Plano e faturas. Se o pagamento já foi feito, "
                    + "desconsidere este aviso.";
            botao = "Pagar a fatura";
        } else if (tipo == TipoNotificacao.ESCOLA_SUSPENSA) {
            assunto = "O acesso de " + escola + " ao Eduka foi suspenso";
            titulo = "Acesso suspenso";
            mensagem = ("O acesso de %s ao Eduka foi suspenso porque a fatura vencida em %s não foi paga no prazo "
                    + "do aviso.").formatted(escola, vencimento);
            fechamento = "Para reativar, fale com o suporte do Eduka (contato@eduka.com.br).";
            botao = "Acessar o Eduka";
        } else {
            throw new IllegalArgumentException("Tipo sem e-mail de suspensao: " + tipo);
        }
        String valor = moeda(fatura.getValor());
        String detalhes = "Fatura: %s\nValor: %s\nVencimento: %s%s".formatted(fatura.getDescricao(), valor, vencimento,
                tipo == TipoNotificacao.AVISO_SUSPENSAO ? "\nSuspensão em: " + dia : "");

        String texto = """
                Olá, %s!

                %s

                %s

                %s
                Acesse: %s

                Este é um e-mail automático, não responda.
                """.formatted(nome, mensagem, detalhes, fechamento, urlLogin);
        String html = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><title>%1$s</title></head>
                <body style="margin:0; padding:0; background-color:#f8f3ff;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="background-color:#f8f3ff;">
                  <tr><td align="center" style="padding:32px 16px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                           style="max-width:560px; background-color:#ffffff; border-radius:12px;
                                  font-family:Arial, Helvetica, sans-serif; color:#3d3550;">
                      <tr><td style="background-color:#6a3fc4; border-radius:12px 12px 0 0; padding:24px 32px;">
                        <img src="%2$s" width="152" height="40" alt="Eduka" style="display:block; border:0;">
                      </td></tr>
                      <tr><td style="padding:32px 32px 8px 32px;">
                        <h1 style="margin:0 0 16px 0; font-size:20px; line-height:28px;">Olá, %3$s!</h1>
                        <p style="margin:0 0 24px 0; font-size:15px; line-height:24px; color:#5f5873;">%4$s</p>
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                               style="background-color:#fdecec; border-radius:8px;">
                          <tr><td style="padding:16px 20px; font-size:15px; line-height:24px;">%5$s</td></tr>
                        </table>
                        <p style="margin:24px 0 0 0; font-size:14px; line-height:22px; color:#5f5873;">%6$s</p>
                      </td></tr>
                      <tr><td align="center" style="padding:24px 32px 32px 32px;">
                        <a href="%7$s" target="_blank" style="display:inline-block; padding:14px 32px; font-size:16px;
                           font-weight:bold; color:#ffffff; text-decoration:none; border-radius:8px;
                           background-color:#6a3fc4;">%8$s</a>
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(escapar(titulo), urlLogo, escapar(nome), escapar(mensagem),
                escapar(detalhes).replace("\n", "<br>\n"), escapar(fechamento), urlLogin, escapar(botao));
        return new Email(destinatario, nome, assunto, html, texto);
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor);
    }

    private static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto == null ? "" : texto);
    }
}
