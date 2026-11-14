package br.pucminas.eduka.backend.service.auditoria;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.LogAuditoria;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.auditoria.OrigemAuditoria;
import br.pucminas.eduka.backend.repository.auditoria.LogAuditoriaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Grava no {@code LOG_AUDITORIA} quem fez cada operacao (RNF05): o
 * financeiro, a equipe, alunos, professores, matriculas, avaliacoes, notas e
 * a propria escola.
 *
 * <p>
 * Chamado pelos services depois de salvar o registro, dentro da mesma
 * transacao ({@link Propagation#MANDATORY}): o log so existe se a operacao
 * foi gravada, e uma falha ao gravar o log desfaz a operacao. Usuario e
 * escola vem sempre do token, nunca do cliente.
 * </p>
 *
 * <p>
 * O que o provedor de pagamento confirma chega sem usuario: vai por
 * {@link #registrarDoProvedor}, sem autor e com a escola do proprio registro.
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
                .origem(OrigemAuditoria.USUARIO)
                .operacao(operacao)
                .entidade(entidade)
                .registroId(registroId)
                .dataHora(clock.instant())
                .build());
    }

    /**
     * Registra que o usuario autenticado fez {@code operacao} num registro de
     * outra escola que nao a do token: o SUPER_ADMIN, que nao tem escola,
     * mexendo numa escola. A linha fica com a escola afetada, para o MASTER
     * dela ver no registro de atividades.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarNaEscola(OperacaoAuditoria operacao, EntidadeAuditada entidade, UUID registroId,
            UUID escolaId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        logAuditoriaRepository.save(LogAuditoria.builder()
                .escolaId(escolaId)
                .usuarioId(usuario.id())
                .origem(OrigemAuditoria.USUARIO)
                .operacao(operacao)
                .entidade(entidade)
                .registroId(registroId)
                .dataHora(clock.instant())
                .build());
    }

    /**
     * Registra uma operacao que uma rotina do proprio Eduka fez, sem usuario
     * (ex.: suspender a escola por fatura em atraso).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarDoSistema(OperacaoAuditoria operacao, EntidadeAuditada entidade, UUID registroId,
            UUID escolaId) {
        logAuditoriaRepository.save(LogAuditoria.builder()
                .escolaId(escolaId)
                .origem(OrigemAuditoria.SISTEMA)
                .operacao(operacao)
                .entidade(entidade)
                .registroId(registroId)
                .dataHora(clock.instant())
                .build());
    }

    /**
     * Registra uma operacao que o provedor de pagamento originou (aviso do
     * webhook ou consulta), sem autor. A escola vem do registro afetado, nunca
     * do aviso.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarDoProvedor(OperacaoAuditoria operacao, EntidadeAuditada entidade, UUID registroId,
            UUID escolaId) {
        logAuditoriaRepository.save(LogAuditoria.builder()
                .escolaId(escolaId)
                .origem(OrigemAuditoria.PROVEDOR)
                .operacao(operacao)
                .entidade(entidade)
                .registroId(registroId)
                .dataHora(clock.instant())
                .build());
    }
}
