package br.pucminas.eduka.backend.service.escola;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.exception.escola.LimiteUsuariosAtingidoException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;

/**
 * Limite de usuarios do plano da escola (S5-11): quantos logins ativos ela
 * pode ter, de todos os perfis. Conta na hora de criar um login novo e de
 * reativar um inativo; inativar libera a vaga. Escola sem limite nao tem
 * restricao.
 *
 * <p>
 * Baixar o limite abaixo do que a escola ja usa nao derruba ninguem: so
 * barra os proximos ate a escola voltar para dentro dele.
 * </p>
 */
@Service
public class LimiteUsuariosService {

    private final EscolaRepository escolaRepository;
    private final UsuarioRepository usuarioRepository;

    public LimiteUsuariosService(EscolaRepository escolaRepository, UsuarioRepository usuarioRepository) {
        this.escolaRepository = escolaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Garante uma vaga para mais um login ativo na escola. Trava a linha da
     * escola ate o fim da transacao de quem chama, para dois cadastros ao
     * mesmo tempo nao passarem juntos do limite.
     *
     * @throws LimiteUsuariosAtingidoException (409) se a escola ja esta no limite
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void exigirVaga(UUID escolaId) {
        if (escolaId == null) {
            return;
        }
        Integer limite = escolaRepository.travarLimiteUsuarios(escolaId).orElse(null);
        if (limite != null && usuarioRepository.countByEscolaIdAndAtivoTrue(escolaId) >= limite) {
            throw new LimiteUsuariosAtingidoException(limite);
        }
    }
}
