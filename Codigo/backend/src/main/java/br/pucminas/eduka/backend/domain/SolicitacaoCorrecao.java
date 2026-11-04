package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

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
 * Pedido do aluno para a secretaria corrigir os dados cadastrais (IU15).
 * Mapeia {@code SOLICITACAO_CORRECAO} (V37): uma aberta por aluno de cada vez.
 */
@Entity
@Table(name = "SOLICITACAO_CORRECAO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitacaoCorrecao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_SOLICITACAO_CORRECAO", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "ALUNO_ID", nullable = false, updatable = false)
    private UUID alunoId;

    @Column(name = "MENSAGEM", nullable = false, length = 500, updatable = false)
    private String mensagem;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private StatusSolicitacaoCorrecao status = StatusSolicitacaoCorrecao.ABERTA;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATENDIDA_EM")
    private Instant atendidaEm;
}
