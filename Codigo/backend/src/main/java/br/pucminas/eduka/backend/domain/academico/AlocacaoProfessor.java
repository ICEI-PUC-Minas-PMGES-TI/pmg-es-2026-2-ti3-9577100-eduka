package br.pucminas.eduka.backend.domain.academico;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

import br.pucminas.eduka.backend.domain.pessoas.Professor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Professor alocado para lecionar uma disciplina em uma turma. Mapeia a
 * tabela {@code ALOCACAO_PROFESSOR} (V15): cada par turma/disciplina tem no
 * maximo um professor, e a disciplina precisa fazer parte da grade da turma.
 *
 * <p>
 * {@code escolaId} e o tenant, copiado do token (ver
 * {@link br.pucminas.eduka.backend.service.academico.AlocacaoProfessorService}).
 * </p>
 */
@Entity
@Table(name = "ALOCACAO_PROFESSOR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlocacaoProfessor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_ALOCACAO", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PROFESSOR_ID", nullable = false, updatable = false)
    private Professor professor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TURMA_ID", nullable = false, updatable = false)
    private Turma turma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DISCIPLINA_ID", nullable = false, updatable = false)
    private Disciplina disciplina;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;
}
