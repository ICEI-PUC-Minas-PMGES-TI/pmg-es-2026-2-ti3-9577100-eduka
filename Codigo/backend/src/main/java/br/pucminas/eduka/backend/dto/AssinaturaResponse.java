package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.PlanoEscola;

/**
 * Assinatura da escola do token (IU24-A, "Sua assinatura"): plano, situacao,
 * vigencia e limite de usuarios. Os dados sao os do cadastro feito pelo
 * SUPER_ADMIN; o MASTER so consulta.
 */
public record AssinaturaResponse(
        String escolaNome,
        PlanoEscola plano,
        boolean ativa,
        LocalDate inicioVigencia,
        LocalDate fimVigencia,
        Integer limiteUsuarios) {

    public static AssinaturaResponse de(Escola escola) {
        return new AssinaturaResponse(escola.getNome(), escola.getPlano(), escola.isAtivo(),
                escola.getInicioVigencia(), escola.getFimVigencia(), escola.getLimiteUsuarios());
    }
}
