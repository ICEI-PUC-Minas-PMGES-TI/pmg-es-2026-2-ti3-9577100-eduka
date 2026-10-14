package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.LogAuditoria;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.repository.LogAuditoriaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Grava no {@code LOG_AUDITORIA} quem fez cada operacao financeira (RNF05).
 *
 * <p>
 * Chamado pelos services depois de salvar o registro, dentro da mesma
 * transacao ({@link Propagation#MANDATORY}): o log so existe se a operacao
 * foi gravada, e uma falha ao gravar o log desfaz a operacao. Usuario e
 * escola vem sempre do token, nunca do cliente.
 * </p>
 *
 * <pre>
 * Cobranca salva = cobrancaRepository.saveAndFlush(cobranca);
 * auditoriaService.registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, salva.getId());
 * </pre>
 */
@Service
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public AuditoriaService(LogAuditoriaRepository logAuditoriaRepository, IsolamentoEscola isolamentoEscola,
            Clock clock) {
        this.logAuditoriaRepository = logAuditoriaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    /**
     * Registra que o usuario autenticado fez {@code operacao} no registro
     * {@code registroId} da {@code entidade}.
     *
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
     *         se nao houver usuario autenticado: operacao auditada sem autor
     *         nao e aceita
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(OperacaoAuditoria operacao, EntidadeAuditada entidade, UUID registroId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        logAuditoriaRepository.save(LogAuditoria.builder()
                .escolaId(usuario.escolaId())
                .usuarioId(usuario.id())
                .operacao(operacao)
                .entidade(entidade)
                .registroId(registroId)
                .dataHora(clock.instant())
                .build());
    }
}
