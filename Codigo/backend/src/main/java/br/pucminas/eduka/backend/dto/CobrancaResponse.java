package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.MatriculaTurma;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.StatusCobranca;

public record CobrancaResponse(
        UUID id,
        UUID alunoId,
        String alunoNome,
        /** Numero de matricula do aluno na escola (nao o id da matricula na turma). */
        String alunoNumeroMatricula,
        UUID matriculaId,
        Long turmaId,
        String turmaNome,
        UUID responsavelId,
        String responsavelNome,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        /** Ja com EM_ATRASO no lugar de PENDENTE vencida que a rotina diaria ainda nao marcou. */
        StatusCobranca status,
        Instant canceladaEm,
        Instant pagaEm,
        /** Pagamento online ou baixa manual, com o link do comprovante; nulo enquanto nao paga. */
        PagamentoDaCobrancaResponse pagamento,
        Instant criadoEm,
        Instant atualizadoEm) {

    /**
     * {@code pessoaAluno} e {@code responsavel} guardam os nomes (cifrados);
     * {@code hoje} decide se a cobranca pendente ja esta em atraso.
     */
    public static CobrancaResponse de(Cobranca cobranca, Pessoa pessoaAluno, Pessoa responsavel, LocalDate hoje) {
        return de(cobranca, pessoaAluno, responsavel, null, hoje);
    }

    public static CobrancaResponse de(Cobranca cobranca, Pessoa pessoaAluno, Pessoa responsavel,
            PagamentoDaCobrancaResponse pagamento, LocalDate hoje) {
        Aluno aluno = cobranca.getAluno();
        MatriculaTurma matricula = cobranca.getMatricula();
        return new CobrancaResponse(cobranca.getId(), aluno.getId(),
                pessoaAluno != null ? pessoaAluno.getNome() : null, aluno.getMatricula(),
                matricula != null ? matricula.getId() : null,
                matricula != null ? matricula.getTurma().getId() : null,
                matricula != null ? matricula.getTurma().getNome() : null,
                cobranca.getResponsavelId(), responsavel != null ? responsavel.getNome() : null,
                cobranca.getDescricao(), cobranca.getValor(), cobranca.getDataVencimento(),
                StatusCobranca.efetivo(cobranca.getStatus(), cobranca.getDataVencimento(), hoje),
                cobranca.getCanceladaEm(), cobranca.getPagaEm(), pagamento, cobranca.getCriadoEm(),
                cobranca.getAtualizadoEm());
    }
}
