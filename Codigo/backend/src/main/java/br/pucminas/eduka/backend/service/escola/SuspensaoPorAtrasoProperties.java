package br.pucminas.eduka.backend.service.escola;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prazos da suspensao por fatura em atraso ({@code app.assinatura.suspensao.*}).
 *
 * @param avisoAposDias     dias depois do vencimento em que sai o aviso ao MASTER
 * @param diasAteSuspender  dias entre o aviso e a suspensao, se a fatura
 *                          continuar em aberto
 */
@ConfigurationProperties("app.assinatura.suspensao")
public record SuspensaoPorAtrasoProperties(int avisoAposDias, int diasAteSuspender) {

    public SuspensaoPorAtrasoProperties {
        if (avisoAposDias < 0 || diasAteSuspender < 0) {
            throw new IllegalArgumentException("Os prazos da suspensao nao podem ser negativos");
        }
    }
}
