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
 * Prova, trabalho ou tarefa de uma disciplina numa turma. Mapeia a tabela
 * {@code AVALIACAO} (V19). {@code valor} e a pontuacao maxima: a nota de cada
 * aluno ({@link Nota}) vai de 0 ate ele.
 *
 * <p>
 * {@code escolaId} e o tenant, copiado do token (ver
 * {@link br.pucminas.eduka.backend.service.AvaliacaoService}).
 * </p>
 */
@Entity
@Table(name = "AVALIACAO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_AVALIACAO", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TURMA_ID", nullable = false, updatable = false)
    private Turma turma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DISCIPLINA_ID", nullable = false, updatable = false)
    private Disciplina disciplina;

    /** Quem criou. Quem gerencia e o professor alocado hoje na turma/disciplina. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PROFESSOR_ID", nullable = false, updatable = false)
    private Professor professor;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 20)
    private TipoAvaliacao tipo;

    @Column(name = "TITULO", nullable = false, length = 150)
    private String titulo;

    @Column(name = "DESCRICAO", length = 500)
    private String descricao;

    @Column(name = "DATA_AVALIACAO", nullable = false)
    private LocalDate data;

    @Column(name = "VALOR", nullable = false, precision = 5, scale = 2)
    private BigDecimal valor;

    /** Falso enquanto as notas estao em rascunho (IU14): o aluno ainda nao as ve. */
    @Builder.Default
    @Column(name = "NOTAS_PUBLICADAS", nullable = false)
    private boolean notasPublicadas = true;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;
}
