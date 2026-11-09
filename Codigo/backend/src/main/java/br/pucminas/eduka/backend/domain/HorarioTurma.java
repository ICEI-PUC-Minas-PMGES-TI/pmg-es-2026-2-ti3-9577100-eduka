package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.time.LocalTime;
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
 * Uma aula da grade semanal de uma turma. Mapeia a tabela
 * {@code HORARIO_TURMA} (V36): a alocacao diz turma, disciplina e
 * professor; aqui entram o dia e o intervalo de horas.
 *
 * <p>
 * {@code escolaId} e o tenant, copiado do token (ver
 * {@link br.pucminas.eduka.backend.service.HorarioTurmaService}).
 * </p>
 */
@Entity
@Table(name = "HORARIO_TURMA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioTurma {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_HORARIO_TURMA", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    /** Nao muda: para trocar a disciplina ou o professor, remove-se a aula e cria-se outra. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ALOCACAO_ID", nullable = false, updatable = false)
    private AlocacaoProfessor alocacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "DIA_SEMANA", nullable = false, length = 10)
    private DiaSemana diaSemana;

    @Column(name = "HORA_INICIO", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "HORA_FIM", nullable = false)
    private LocalTime horaFim;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;
}
