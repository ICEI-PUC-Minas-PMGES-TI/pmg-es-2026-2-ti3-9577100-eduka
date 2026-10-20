package br.pucminas.eduka.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
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
 * Pagamento de uma cobranca. Mapeia a tabela {@code PAGAMENTO_COBRANCA} (V27,
 * V28): no maximo um por cobranca, sempre da mesma escola dela (FK composta).
 *
 * <p>
 * Sem {@code @TenantId}: o pagamento online e gravado pelo webhook, que chega
 * sem usuario autenticado. {@code escolaId} e preenchido a mao com a escola da
 * cobranca, e toda consulta filtra por ele a mao (como em
 * {@link EventoProvedor}).
 * </p>
 */
@Entity
@Table(name = "PAGAMENTO_COBRANCA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagamentoCobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_PAGAMENTO", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "COBRANCA_ID", nullable = false, updatable = false, unique = true)
    private UUID cobrancaId;

    @Column(name = "VALOR_PAGO", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago;

    /** Valor que entra na conta da escola, ja sem a taxa do provedor. */
    @Column(name = "VALOR_LIQUIDO", precision = 10, scale = 2)
    private BigDecimal valorLiquido;

    @Column(name = "DATA_PAGAMENTO", nullable = false)
    private LocalDate dataPagamento;

    @Column(name = "DATA_CREDITO")
    private LocalDate dataCredito;

    @Enumerated(EnumType.STRING)
    @Column(name = "FORMA_PAGAMENTO", nullable = false, length = 6)
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORIGEM", nullable = false, length = 6, updatable = false)
    private OrigemPagamento origem;

    @Column(name = "ID_TRANSACAO_EXTERNA", nullable = false, length = 100)
    private String idTransacaoExterna;

    /** So auditoria; nunca sai do backend. */
    @Column(name = "URL_RECIBO_PROVEDOR", length = 500)
    private String urlReciboProvedor;

    /** Evento do webhook que confirmou o pagamento. Nulo se veio da reconciliacao. */
    @Column(name = "EVENTO_ID", updatable = false)
    private UUID eventoId;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "REGISTRADO_EM", nullable = false, updatable = false)
    private Instant registradoEm;

    /** A URL do recibo leva token do provedor: fora de log. */
    @Override
    public String toString() {
        return "PagamentoCobranca[id=%s, escolaId=%s, cobrancaId=%s, origem=%s]"
                .formatted(id, escolaId, cobrancaId, origem);
    }
}
