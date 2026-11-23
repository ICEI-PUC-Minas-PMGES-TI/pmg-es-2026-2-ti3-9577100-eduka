package br.pucminas.eduka.backend.service.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Publicado por {@link AvisoCobrancaService} quando uma cobranca precisa
 * virar e-mail; enviado por {@link CobrancaEmailListener} depois do commit.
 * Ja chega com destinatario e nomes decifrados.
 *
 * @param destinatarioEhAluno se o e-mail vai para o proprio aluno (sem
 *                            responsavel financeiro com e-mail)
 * @param pagaEm              so em {@link Tipo#PAGAMENTO_CONFIRMADO}
 */
public record AvisoCobranca(
        Tipo tipo,
        UUID cobrancaId,
        String destinatarioEmail,
        String destinatarioNome,
        boolean destinatarioEhAluno,
        String alunoNome,
        String escolaNome,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        Instant pagaEm) {

    public enum Tipo {
        COBRANCA_CRIADA,
        VENCIMENTO_PROXIMO,
        PAGAMENTO_CONFIRMADO
    }

    /** Destinatario e nomes sao dado pessoal: fora de log. */
    @Override
    public String toString() {
        return "AvisoCobranca[tipo=%s, cobrancaId=%s]".formatted(tipo, cobrancaId);
    }
}
