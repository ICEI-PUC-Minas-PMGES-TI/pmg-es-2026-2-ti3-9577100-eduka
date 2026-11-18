package br.pucminas.eduka.backend.domain.financeiro;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import br.pucminas.eduka.backend.pagamento.tipos.TipoEventoProvedor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Aviso recebido do provedor (webhook), guardado antes de ser aplicado. Mapeia
 * a tabela {@code EVENTO_PROVEDOR} (V27). O id externo e unico: o mesmo aviso
 * chegando duas vezes e aplicado uma vez so.
 *
 * <p>
 * Sem {@code @TenantId}: o evento chega sem escola. {@code escolaId} e
 * preenchido quando a origem e resolvida, e toda consulta filtra por ele a mao.
 * </p>
 *
 * <p>
 * Nao confundir com {@link br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor},
 * o evento ja traduzido pelo adaptador.
 * </p>
 */
@Entity
@Table(name = "EVENTO_PROVEDOR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoProvedor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_EVENTO", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ID_EVENTO_EXTERNO", nullable = false, length = 150, unique = true, updatable = false)
    private String idEventoExterno;

    /** Nome do evento como o provedor mandou (ex.: {@code PAYMENT_RECEIVED}). */
    @Column(name = "TIPO_ORIGINAL", nullable = false, length = 60, updatable = false)
    private String tipoOriginal;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 30, updatable = false)
    private TipoEventoProvedor tipo;

    @Column(name = "REFERENCIA_EXTERNA", length = 60)
    private String referenciaExterna;

    @Column(name = "ID_CONTA_EXTERNA", length = 64)
    private String idContaExterna;

    @Column(name = "ESCOLA_ID")
    private UUID escolaId;

    /** JSON recebido, ja sem dados pessoais nem bancarios. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "PAYLOAD", nullable = false, updatable = false)
    private String payload;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 12)
    private StatusEventoProvedor status = StatusEventoProvedor.RECEBIDO;

    @Column(name = "MOTIVO", length = 500)
    private String motivo;

    @Builder.Default
    @Column(name = "TENTATIVAS", nullable = false)
    private int tentativas = 0;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "RECEBIDO_EM", nullable = false, updatable = false)
    private Instant recebidoEm;

    @Column(name = "PROCESSADO_EM")
    private Instant processadoEm;
}
