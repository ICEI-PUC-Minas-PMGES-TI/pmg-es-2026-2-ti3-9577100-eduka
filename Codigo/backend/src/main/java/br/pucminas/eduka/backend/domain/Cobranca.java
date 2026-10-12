package br.pucminas.eduka.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cobranca lancada para um aluno. Mapeia a tabela {@code COBRANCA} (V20):
 * cancelar muda o {@link StatusCobranca} em vez de apagar o registro.
 *
 * <p>
 * {@code escolaId} e o tenant, copiado do token (ver
 * {@link br.pucminas.eduka.backend.service.CobrancaService}).
 * </p>
 */
@Entity
@Table(name = "COBRANCA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_COBRANCA", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ALUNO_ID", nullable = false, updatable = false)
    private Aluno aluno;

    /** Matricula que originou a cobranca. Nula em cobranca avulsa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MATRICULA_ID")
    private MatriculaTurma matricula;

    /** {@link Pessoa} do responsavel financeiro. Nulo quando o proprio aluno paga. */
    @Column(name = "RESPONSAVEL_ID")
    private UUID responsavelId;

    @Column(name = "DESCRICAO", nullable = false, length = 255)
    private String descricao;

    @Column(name = "VALOR", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "DATA_VENCIMENTO", nullable = false)
    private LocalDate dataVencimento;

    /** Nunca {@link StatusCobranca#EM_ATRASO}, que e calculado na leitura. */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatusCobranca status;

    @Column(name = "CANCELADA_EM")
    private Instant canceladaEm;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;
}
