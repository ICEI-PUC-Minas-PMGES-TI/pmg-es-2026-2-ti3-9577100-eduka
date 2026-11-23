package br.pucminas.eduka.backend.domain.academico;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

import br.pucminas.eduka.backend.domain.pessoas.Aluno;
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
 * Matricula de um aluno numa turma. Mapeia a tabela {@code MATRICULA_TURMA}
 * (V18): um registro por aluno e turma, cuja situacao muda pelo
 * {@link StatusMatricula} em vez de ser apagado.
 *
 * <p>
 * {@code escolaId} e o tenant, copiado do token (ver
 * {@link br.pucminas.eduka.backend.service.academico.MatriculaTurmaService}).
 * </p>
 */
@Entity
@Table(name = "MATRICULA_TURMA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatriculaTurma {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_MATRICULA", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ALUNO_ID", nullable = false, updatable = false)
    private Aluno aluno;

    /** Muda na transferencia para outra turma. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TURMA_ID", nullable = false)
    private Turma turma;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatusMatricula status;

    @Column(name = "DATA_MATRICULA", nullable = false)
    private LocalDate dataMatricula;

    @Column(name = "OBSERVACAO", length = 500)
    private String observacao;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;
}
