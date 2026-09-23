package br.pucminas.eduka.backend.security;

import static br.pucminas.eduka.backend.domain.PerfilUsuario.ALUNO;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.FINANCEIRO;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.MASTER;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.PROFESSOR;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.SECRETARIA;
import static br.pucminas.eduka.backend.domain.PerfilUsuario.SUPER_ADMIN;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import br.pucminas.eduka.backend.domain.PerfilUsuario;

/**
 * Quem pode dar login a quem. Regra de servico, nao de rota: a rota
 * {@code /usuarios} e a mesma para todos, o que muda e o perfil pedido.
 *
 * <ul>
 * <li>SUPER_ADMIN cria o MASTER de cada escola (e qualquer perfil abaixo).</li>
 * <li>MASTER cria a equipe e os alunos da propria escola.</li>
 * <li>SECRETARIA cria login de professor e aluno.</li>
 * <li>Ninguem cria SUPER_ADMIN pela API.</li>
 * </ul>
 */
public final class HierarquiaPerfis {

    private static final Map<PerfilUsuario, Set<PerfilUsuario>> PODE_CRIAR = Map.of(
            SUPER_ADMIN, EnumSet.of(MASTER, SECRETARIA, FINANCEIRO, PROFESSOR, ALUNO),
            MASTER, EnumSet.of(SECRETARIA, FINANCEIRO, PROFESSOR, ALUNO),
            SECRETARIA, EnumSet.of(PROFESSOR, ALUNO));

    private HierarquiaPerfis() {
    }

    public static boolean podeCriar(PerfilUsuario criador, PerfilUsuario novo) {
        return perfisQuePodeCriar(criador).contains(novo);
    }

    public static Set<PerfilUsuario> perfisQuePodeCriar(PerfilUsuario criador) {
        return PODE_CRIAR.getOrDefault(criador, Set.of());
    }
}
