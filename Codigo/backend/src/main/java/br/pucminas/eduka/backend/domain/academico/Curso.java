package br.pucminas.eduka.backend.domain.academico;

import br.pucminas.eduka.backend.domain.escola.Escola;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "curso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escola_id", nullable = false)
    private Escola escola;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 50)
    private String codigo;

    @Column(length = 500)
    private String descricao;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
}
