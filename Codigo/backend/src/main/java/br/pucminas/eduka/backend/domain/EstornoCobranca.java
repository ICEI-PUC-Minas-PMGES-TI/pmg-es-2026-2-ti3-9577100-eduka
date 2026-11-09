package br.pucminas.eduka.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dinheiro devolvido ao pagador de uma cobranca. Mapeia a tabela
 * {@code ESTORNO_COBRANCA} (V41): uma linha por estorno, porque um pagamento
 * pode ter varios parciais, cada um na sua data. A soma fica em
 * {@link PagamentoCobranca#getValorEstornado()}.
 *
 * <p>
 * Sem {@code @TenantId}, como {@link PagamentoCobranca}: e gravado pelo
 * webhook, sem usuario autenticado, e toda consulta filtra a escola a mao.
 * </p>
 */
@Entity
@Table(name = "ESTORNO_COBRANCA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstornoCobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_ESTORNO", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "COBRANCA_ID", nullable = false, updatable = false)
    private UUID cobrancaId;

    @Column(name = "VALOR", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    /** Quando o dinheiro saiu: define em que periodo o estorno desconta. */
    @Column(name = "ESTORNADO_EM", nullable = false, updatable = false)
    private Instant estornadoEm;

    /** Evento do webhook que trouxe o estorno. */
    @Column(name = "EVENTO_ID", updatable = false)
    private UUID eventoId;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "REGISTRADO_EM", nullable = false, updatable = false)
    private Instant registradoEm;
}
