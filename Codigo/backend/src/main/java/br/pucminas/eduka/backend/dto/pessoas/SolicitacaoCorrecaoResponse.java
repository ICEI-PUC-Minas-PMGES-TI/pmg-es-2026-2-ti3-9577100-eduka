package br.pucminas.eduka.backend.dto.pessoas;

import java.time.Instant;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.pessoas.SolicitacaoCorrecao;
import br.pucminas.eduka.backend.domain.pessoas.StatusSolicitacaoCorrecao;

/**
 * Pedido de correcao de dados (IU15). {@code alunoNome} e
 * {@code alunoMatricula} so vem na lista da secretaria.
 */
public record SolicitacaoCorrecaoResponse(
        UUID id,
        UUID alunoId,
        String alunoNome,
        String alunoMatricula,
        String mensagem,
        StatusSolicitacaoCorrecao status,
        Instant criadoEm,
        Instant atendidaEm) {

    public static SolicitacaoCorrecaoResponse de(SolicitacaoCorrecao s, String alunoNome, String alunoMatricula) {
        return new SolicitacaoCorrecaoResponse(s.getId(), s.getAlunoId(), alunoNome, alunoMatricula, s.getMensagem(),
                s.getStatus(), s.getCriadoEm(), s.getAtendidaEm());
    }
}
