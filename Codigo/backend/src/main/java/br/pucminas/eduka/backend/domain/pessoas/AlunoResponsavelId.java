package br.pucminas.eduka.backend.domain.pessoas;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record AlunoResponsavelId(
        @Column(name = "ALUNO_ID") UUID alunoId,
        @Column(name = "PESSOA_ID") UUID pessoaId) implements Serializable {
}
