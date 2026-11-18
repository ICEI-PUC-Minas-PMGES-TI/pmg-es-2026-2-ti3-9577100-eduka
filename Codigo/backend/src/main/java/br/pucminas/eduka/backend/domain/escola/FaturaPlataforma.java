package br.pucminas.eduka.backend.domain.escola;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fatura que a escola paga ao Eduka (Fluxo A). Mapeia {@code FATURA_PLATAFORMA}
 * (V30). Mesmos estados da {@link Cobranca}; o pagamento fica na propria
 * fatura.
 *
 * <p>
 * Sem {@code @TenantId}: o Super Admin lanca e lista faturas de qualquer escola
 * sem ter escola no token. {@code escolaId} entra explicito em toda consulta
 * do MASTER.
 * </p>
 */
@Entity
@Table(name = "FATURA_PLATAFORMA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaturaPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_FATURA", nullable = false, updatable = false)
    private UUID id;

    /** Escola pagadora. */
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    /** Primeiro dia do mes de referencia. */
    @Column(name = "COMPETENCIA", nullable = false, updatable = false)
    private LocalDate competencia;

    @Column(name = "DESCRICAO", nullable = false, length = 255)
    private String descricao;

    @Column(name = "VALOR", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "DATA_VENCIMENTO", nullable = false)
    private LocalDate dataVencimento;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private StatusCobranca status = StatusCobranca.PENDENTE;

    /** So o roteador de pagamentos muda. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO_PROVEDOR", nullable = false, length = 15)
    private SituacaoProvedor situacaoProvedor = SituacaoProvedor.NAO_EMITIDA;

    /** Forma escolhida no ultimo Pagar. Nula enquanto ninguem clicou. */
    @Enumerated(EnumType.STRING)
    @Column(name = "FORMA_PAGAMENTO", length = 6)
    private FormaPagamento formaPagamento;

    /** Id da cobranca na conta-pai. Nunca sai do backend. */
    @Column(name = "ID_COBRANCA_EXTERNA", length = 64, unique = true)
    private String idCobrancaExterna;

    @Column(name = "EMISSAO_INICIADA_EM")
    private Instant emissaoIniciadaEm;

    @Column(name = "EMITIDA_EM")
    private Instant emitidaEm;

    /** Mensagem saneada da ultima recusa do provedor. */
    @Column(name = "ULTIMO_ERRO_PROVEDOR", length = 500)
    private String ultimoErroProvedor;

    @Column(name = "VALOR_PAGO", precision = 10, scale = 2)
    private BigDecimal valorPago;

    @Column(name = "DATA_PAGAMENTO")
    private LocalDate dataPagamento;

    /** Codigo da transacao no provedor; vai no comprovante (R30). */
    @Column(name = "ID_TRANSACAO_EXTERNA", length = 100)
    private String idTransacaoExterna;

    /** Recibo do provedor. Nunca sai do backend (R17). */
    @Column(name = "URL_RECIBO_PROVEDOR", length = 500)
    private String urlReciboProvedor;

    /** Usuario Super Admin que lancou. */
    @Column(name = "CRIADA_POR", nullable = false, updatable = false)
    private UUID criadaPor;

    @Column(name = "CANCELADA_EM")
    private Instant canceladaEm;

    @Column(name = "PAGA_EM")
    private Instant pagaEm;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;

    /** Trava otimista: dois cliques em Pagar criam uma cobranca so no provedor. */
    @Version
    @Column(name = "VERSAO", nullable = false)
    private long versao;
}
