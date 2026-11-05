package br.pucminas.eduka.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "periodo_letivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodoLetivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escola_id", nullable = false)
    private Escola escola;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    /** Periodo planejado so recebe matricula depois de Abrir matriculas (IU09). */
    @Builder.Default
    @Column(name = "matriculas_abertas", nullable = false)
    private boolean matriculasAbertas = true;

    /** Encerrado pela secretaria antes do fim (IU09): nao recebe matricula nova. */
    @Column(name = "encerrado_em")
    private LocalDate encerradoEm;
}
