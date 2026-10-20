package br.pucminas.eduka.backend.pagamento;

import java.util.UUID;

import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.dto.HabilitacaoRecebimentos;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;

/**
 * Casos de uso da conta de recebimento da escola, por tras do
 * {@link PaymentRoutingService}. Por enquanto so a habilitacao (R11); ativar
 * e sincronizar sao da NOVA-3.
 */
@Component
class RoteadorConta {

    private final ContaRecebimentoEscolaRepository contaRepository;
    private final EscolaRepository escolaRepository;

    RoteadorConta(ContaRecebimentoEscolaRepository contaRepository, EscolaRepository escolaRepository) {
        this.contaRepository = contaRepository;
        this.escolaRepository = escolaRepository;
    }

    /**
     * R11: habilitado so com a conta APROVADA e a escola ativa. Escola inativa
     * vem primeiro, porque nem uma conta aprovada a faz receber.
     */
    HabilitacaoRecebimentos habilitacao(UUID escolaId) {
        boolean escolaAtiva = escolaRepository.findById(escolaId).map(Escola::isAtivo).orElse(false);
        if (!escolaAtiva) {
            return naoHabilitado("ESCOLA_INATIVA");
        }
        return contaRepository.findByEscolaId(escolaId)
                .map(RoteadorConta::habilitacaoDaConta)
                .orElseGet(() -> naoHabilitado("NAO_INICIADA"));
    }

    private static HabilitacaoRecebimentos habilitacaoDaConta(ContaRecebimentoEscola conta) {
        return switch (conta.getStatus()) {
            case APROVADA -> new HabilitacaoRecebimentos(true, null);
            case CRIANDO, VERIFICACAO_PENDENTE -> naoHabilitado("EM_CRIACAO");
            case ERRO_CRIACAO -> naoHabilitado("ERRO_CRIACAO");
            case AGUARDANDO_APROVACAO -> naoHabilitado("AGUARDANDO_APROVACAO");
            case REJEITADA -> naoHabilitado("REJEITADA");
            case CREDENCIAL_INDISPONIVEL -> naoHabilitado("CREDENCIAL_INDISPONIVEL");
        };
    }

    private static HabilitacaoRecebimentos naoHabilitado(String motivo) {
        return new HabilitacaoRecebimentos(false, motivo);
    }
}
