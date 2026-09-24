package br.pucminas.eduka.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
