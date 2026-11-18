package br.pucminas.eduka.backend.domain.financeiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import br.pucminas.eduka.backend.pagamento.tipos.SituacaoCadastral;
import br.pucminas.eduka.backend.pagamento.tipos.TipoEmpresa;
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
 * Conta filha (subconta) da escola no provedor de pagamento. Mapeia a tabela
 * {@code CONTA_RECEBIMENTO_ESCOLA} (V25): uma por escola.
 *
 * <p>
 * Agencia, conta e digito nunca sao gravados: so {@code bancoCodigo} e os 4
 * ultimos digitos ({@code contaFinal}), para exibir. A chave da conta fica em
 * {@code credencialCifrada} e so o roteador a decifra.
 * </p>
 *
 * <p>
 * {@code escolaId} e o tenant: nunca vem do cliente.
 * </p>
 */
@Entity
@Table(name = "CONTA_RECEBIMENTO_ESCOLA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContaRecebimentoEscola {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_CONTA_RECEBIMENTO", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private StatusContaRecebimento status;

    @Column(name = "RAZAO_SOCIAL", nullable = false, length = 150)
    private String razaoSocial;

    @Column(name = "EMAIL_FINANCEIRO", nullable = false, length = 150)
    private String emailFinanceiro;

    @Column(name = "TELEFONE_CELULAR", nullable = false, length = 11)
    private String telefoneCelular;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_EMPRESA", nullable = false, length = 20)
    private TipoEmpresa tipoEmpresa;

    @Column(name = "FATURAMENTO_MENSAL", nullable = false, precision = 14, scale = 2)
    private BigDecimal faturamentoMensal;

    @Column(name = "LOGRADOURO", nullable = false, length = 200)
    private String logradouro;

    @Column(name = "NUMERO", nullable = false, length = 10)
    private String numero;

    @Column(name = "COMPLEMENTO", length = 100)
    private String complemento;

    @Column(name = "BAIRRO", nullable = false, length = 100)
    private String bairro;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CEP", nullable = false, length = 8)
    private String cep;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BANCO_CODIGO", nullable = false, length = 3)
    private String bancoCodigo;

    /** So os 4 ultimos digitos da conta bancaria. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CONTA_FINAL", nullable = false, length = 4)
    private String contaFinal;

    @Column(name = "ID_CONTA_EXTERNA", length = 64, unique = true)
    private String idContaExterna;

    @Column(name = "WALLET_ID", length = 64, unique = true)
    private String walletId;

    /** Chave da conta no provedor, cifrada pelo {@code PiiCryptoService}. */
    @Column(name = "CREDENCIAL_CIFRADA")
    private byte[] credencialCifrada;

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO_GERAL", length = 12)
    private SituacaoCadastral situacaoGeral;

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO_DOCUMENTACAO", length = 12)
    private SituacaoCadastral situacaoDocumentacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO_DADOS_COMERCIAIS", length = 12)
    private SituacaoCadastral situacaoDadosComerciais;

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO_CONTA_BANCARIA", length = 12)
    private SituacaoCadastral situacaoContaBancaria;

    /** Mensagem saneada da ultima recusa do provedor. */
    @Column(name = "ULTIMO_ERRO", length = 500)
    private String ultimoErro;

    @Column(name = "SOLICITADA_EM", nullable = false)
    private Instant solicitadaEm;

    @Column(name = "APROVADA_EM")
    private Instant aprovadaEm;

    @Column(name = "SINCRONIZADA_EM")
    private Instant sincronizadaEm;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;

    @Version
    @Column(name = "VERSAO", nullable = false)
    private long versao;

    /** Nunca imprime a credencial cifrada nem dados de contato. */
    @Override
    public String toString() {
        return "ContaRecebimentoEscola[id=%s, escolaId=%s, status=%s]".formatted(id, escolaId, status);
    }
}
