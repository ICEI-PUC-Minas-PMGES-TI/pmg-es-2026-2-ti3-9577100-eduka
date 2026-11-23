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
 * Despesa da escola (RF18). Mapeia a tabela {@code DESPESA} (V31): cancelar
 * muda a {@link SituacaoDespesa} em vez de apagar o registro.
 *
 * <p>
 * {@code escolaId} e o tenant, copiado do token (ver
 * {@link br.pucminas.eduka.backend.service.DespesaService}).
 * </p>
 */
@Entity
@Table(name = "DESPESA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_DESPESA", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CATEGORIA_ID", nullable = false)
    private CategoriaDespesa categoria;

    @Column(name = "DESCRICAO", nullable = false, length = 255)
    private String descricao;

    /**
     * Nome de quem responde pela despesa, em texto livre (V47). Nulo so nas
     * despesas lancadas antes de o campo existir.
     */
    @Column(name = "RESPONSAVEL", length = 120)
    private String responsavel;

    @Column(name = "VALOR", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "DATA_VENCIMENTO", nullable = false)
    private LocalDate dataVencimento;

    /** So muda pelas regras de {@link SituacaoDespesa#podeMudarPara}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO", nullable = false, length = 20)
    private SituacaoDespesa situacao;

    /** Quando a despesa foi dada como paga. Nula enquanto a pagar. */
    @Column(name = "PAGA_EM")
    private Instant pagaEm;

    @Column(name = "CANCELADA_EM")
    private Instant canceladaEm;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;
}
