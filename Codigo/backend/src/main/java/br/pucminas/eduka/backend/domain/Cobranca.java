package br.pucminas.eduka.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cobranca lancada para um aluno. Mapeia a tabela {@code COBRANCA} (V20, V21, V24, V26):
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

    /**
     * So muda pelas regras de {@link StatusCobranca#podeMudarPara}.
     * {@link StatusCobranca#EM_ATRASO} e gravado pela rotina diaria; ate ela
     * rodar, a PENDENTE vencida ja e exibida como em atraso.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatusCobranca status;

    @Column(name = "CANCELADA_EM")
    private Instant canceladaEm;

    /** Quando a cobranca foi dada como paga (V24). Nula enquanto nao paga. */
    @Column(name = "PAGA_EM")
    private Instant pagaEm;

    /**
     * Quando saiu o e-mail de vencimento proximo para o vencimento atual
     * (V24). Nulo enquanto nao avisado; mudar o vencimento zera.
     */
    @Column(name = "LEMBRETE_VENCIMENTO_EM")
    private Instant lembreteVencimentoEm;

    /** Situacao no provedor de pagamento (V26). So o roteador de pagamentos muda. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO_PROVEDOR", nullable = false, length = 15)
    private SituacaoProvedor situacaoProvedor = SituacaoProvedor.NAO_EMITIDA;

    /** Forma escolhida no ultimo Pagar. Nula enquanto ninguem clicou. */
    @Enumerated(EnumType.STRING)
    @Column(name = "FORMA_PAGAMENTO", length = 6)
    private FormaPagamento formaPagamento;

    /** Id da cobranca no provedor. Nunca sai do backend. */
    @Column(name = "ID_COBRANCA_EXTERNA", length = 64, unique = true)
    private String idCobrancaExterna;

    @Column(name = "EMISSAO_INICIADA_EM")
    private Instant emissaoIniciadaEm;

    @Column(name = "EMITIDA_EM")
    private Instant emitidaEm;

    /** Mensagem saneada da ultima recusa do provedor. */
    @Column(name = "ULTIMO_ERRO_PROVEDOR", length = 500)
    private String ultimoErroProvedor;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;

    /**
     * Trava otimista (V26): edicao da escola e Pagar ao mesmo tempo nao
     * sobrescrevem um ao outro.
     */
    @Version
    @Column(name = "VERSAO", nullable = false)
    private long versao;
}
