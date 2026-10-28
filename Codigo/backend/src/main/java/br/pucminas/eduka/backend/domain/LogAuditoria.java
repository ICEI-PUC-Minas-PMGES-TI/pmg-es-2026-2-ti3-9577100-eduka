package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Uma linha da trilha de auditoria. Mapeia a tabela {@code LOG_AUDITORIA}
 * (V22, V32). Imutavel: so e inserida, por
 * {@link br.pucminas.eduka.backend.service.AuditoriaService}.
 *
 * <p>
 * {@code escolaId} nao e {@code @TenantId} de proposito: e nulo nas acoes do
 * SUPER_ADMIN, que nao tem escola, e o tenant dele nao casaria com a FK.
 * Consultas por escola filtram explicitamente.
 * </p>
 */
@Entity
@Table(name = "LOG_AUDITORIA")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_LOG_AUDITORIA", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ESCOLA_ID", updatable = false)
    private UUID escolaId;

    /** Quem fez a operacao. Nulo so na origem {@link OrigemAuditoria#PROVEDOR}. */
    @Column(name = "USUARIO_ID", updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORIGEM", nullable = false, updatable = false, length = 10)
    private OrigemAuditoria origem;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERACAO", nullable = false, updatable = false, length = 20)
    private OperacaoAuditoria operacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ENTIDADE", nullable = false, updatable = false, length = 60)
    private EntidadeAuditada entidade;

    /** Id do registro afetado, na tabela indicada por {@link #entidade}. */
    @Column(name = "REGISTRO_ID", nullable = false, updatable = false)
    private UUID registroId;

    @Column(name = "DATA_HORA", nullable = false, updatable = false)
    private Instant dataHora;
}
