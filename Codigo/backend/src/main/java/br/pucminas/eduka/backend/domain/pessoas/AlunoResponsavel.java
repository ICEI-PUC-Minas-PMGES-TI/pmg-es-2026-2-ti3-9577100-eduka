package br.pucminas.eduka.backend.domain.pessoas;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vinculo entre aluno e {@link Responsavel} (pela pessoa do responsavel).
 * {@code financeiro} marca quem paga as cobrancas do aluno: no maximo um por
 * aluno (indice unico parcial na V23).
 */
@Entity
@Table(name = "ALUNO_RESPONSAVEL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlunoResponsavel {

    @EmbeddedId
    private AlunoResponsavelId id;

    @Column(name = "PARENTESCO", length = 30)
    private String parentesco;

    @Column(name = "FINANCEIRO", nullable = false)
    private boolean financeiro;
}
