package br.pucminas.eduka.backend.dto.escola;

import java.time.LocalDate;
import java.util.List;

import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.escola.ModuloEscola;
import br.pucminas.eduka.backend.domain.escola.PlanoEscola;

/**
 * Assinatura da escola do token (IU24-A, "Sua assinatura"): plano, situacao,
 * vigencia e limite de usuarios. Os dados sao os do cadastro feito pelo
 * SUPER_ADMIN; o MASTER so consulta.
 *
 * @param limiteUsuarios nulo quando o plano nao tem limite
 * @param usuariosAtivos logins ativos da escola hoje, o que conta no limite
 * @param modulos        modulos contratados (ver {@code RotasPorModulo})
 */
public record AssinaturaResponse(
        String escolaNome,
        PlanoEscola plano,
        boolean ativa,
        LocalDate inicioVigencia,
        LocalDate fimVigencia,
        Integer limiteUsuarios,
        long usuariosAtivos,
        List<ModuloEscola> modulos) {

    public static AssinaturaResponse de(Escola escola, long usuariosAtivos) {
        return new AssinaturaResponse(escola.getNome(), escola.getPlano(), escola.isAtivo(),
                escola.getInicioVigencia(), escola.getFimVigencia(), escola.getLimiteUsuarios(), usuariosAtivos,
                escola.getModulos().stream().sorted().toList());
    }
}
