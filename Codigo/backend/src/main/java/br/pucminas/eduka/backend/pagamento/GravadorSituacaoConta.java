package br.pucminas.eduka.backend.pagamento;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.StatusContaRecebimento;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoCadastral;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository.ContaPorIdExterno;
import br.pucminas.eduka.backend.service.AuditoriaService;

/**
 * Grava a situacao da conta de recebimento lida do provedor, venha ela do
 * Sincronizar ({@link RoteadorConta}) ou do aviso ({@link RoteadorEventos}).
 * Quando o status muda, audita sem autor (RF22.2): quem mudou foi o provedor,
 * mesmo que o Sincronizar tenha sido clicado por alguem.
 */
@Component
class GravadorSituacaoConta {

    private final ContaRecebimentoEscolaRepository contaRepository;
    private final AuditoriaService auditoriaService;

    GravadorSituacaoConta(ContaRecebimentoEscolaRepository contaRepository, AuditoriaService auditoriaService) {
        this.contaRepository = contaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Chamar dentro da transacao de quem grava.
     *
     * @return 1 se a conta foi atualizada, 0 se nao existe ou ainda nao foi
     *         criada no provedor
     */
    @Transactional(propagation = Propagation.MANDATORY)
    int gravar(String idContaExterna, SituacaoContaProvedor situacao, Instant agora) {
        Optional<ContaPorIdExterno> antes = contaRepository.buscarPorIdContaExterna(idContaExterna);
        StatusContaRecebimento status = RoteadorConta.statusDaSituacao(situacao);
        int gravadas = contaRepository.gravarSituacao(idContaExterna, status.name(), nome(situacao.geral()),
                nome(situacao.documentacao()), nome(situacao.dadosComerciais()), nome(situacao.contaBancaria()),
                agora);
        if (gravadas == 1 && antes.isPresent() && !status.name().equals(antes.get().getStatus())) {
            auditoriaService.registrarDoProvedor(OperacaoAuditoria.ALTERAR_SITUACAO,
                    EntidadeAuditada.CONTA_RECEBIMENTO, antes.get().getId(), antes.get().getEscolaId());
        }
        return gravadas;
    }

    private static String nome(SituacaoCadastral situacao) {
        return situacao == null ? null : situacao.name();
    }
}
