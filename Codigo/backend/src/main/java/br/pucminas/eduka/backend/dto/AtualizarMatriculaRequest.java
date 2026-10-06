package br.pucminas.eduka.backend.dto;

import br.pucminas.eduka.backend.domain.StatusMatricula;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Atualizacao parcial de uma matricula: so o que vier preenchido muda.
 *
 * @param status     nova situacao (trancar, cancelar, concluir, reativar),
 *                   respeitando as transicoes de {@link StatusMatricula}
 * @param turmaId    transfere o aluno para outra turma da escola; so vale
 *                   para matricula que fica ATIVA
 * @param observacao substitui a observacao; string vazia apaga
 */
public record AtualizarMatriculaRequest(
        StatusMatricula status,
        Long turmaId,
        @Size(max = 500, message = "observacao deve ter no máximo 500 caracteres") String observacao) {

    @AssertTrue(message = "Informe status, turmaId ou observacao")
    public boolean isAlgumCampoInformado() {
        return status != null || turmaId != null || observacao != null;
    }
}
