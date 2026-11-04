package br.pucminas.eduka.backend.domain;

import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Uma aula semanal de uma disciplina na turma (IU13): dia da semana e hora de
 * inicio. Mapeia {@code HORARIO_AULA} (V38).
 */
@Entity
@Table(name = "HORARIO_AULA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioAula {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_HORARIO_AULA", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "TURMA_ID", nullable = false, updatable = false)
    private Long turmaId;

    @Column(name = "DISCIPLINA_ID", nullable = false, updatable = false)
    private Long disciplinaId;

    /** ISO-8601: 1 = segunda ... 6 = sabado. */
    @Column(name = "DIA_SEMANA", nullable = false)
    private short diaSemana;

    @Column(name = "HORA_INICIO", nullable = false)
    private LocalTime horaInicio;
}
