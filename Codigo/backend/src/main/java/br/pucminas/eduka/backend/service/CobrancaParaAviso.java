package br.pucminas.eduka.backend.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.Cobranca;

/**
 * O que um aviso de cobranca precisa saber da cobranca. Existe para a rotina
 * diaria, que roda sem usuario autenticado e por isso le as cobrancas por SQL
 * nativo (ver
 * {@link br.pucminas.eduka.backend.repository.CobrancaRepository#buscarParaLembreteDeVencimento}),
 * sem passar pela entidade.
 *
 * @param alunoPessoaId       pessoa do aluno
 * @param responsavelPessoaId pessoa do responsavel financeiro; nula quando o
 *                            aluno paga
 */
public record CobrancaParaAviso(
        UUID id,
        UUID escolaId,
        UUID alunoPessoaId,
        UUID responsavelPessoaId,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        Instant pagaEm) {

    public static CobrancaParaAviso de(Cobranca cobranca) {
        return new CobrancaParaAviso(cobranca.getId(), cobranca.getEscolaId(), cobranca.getAluno().getPessoaId(),
                cobranca.getResponsavelId(), cobranca.getDescricao(), cobranca.getValor(),
                cobranca.getDataVencimento(), cobranca.getPagaEm());
    }

    /**
     * Linha do SQL nativo, na ordem das colunas da consulta. O driver pode
     * devolver a data como {@link Date} ou {@link LocalDate}, conforme a
     * versao do Hibernate; aceita as duas.
     */
    public static CobrancaParaAviso deLinha(Object[] linha) {
        return new CobrancaParaAviso((UUID) linha[0], (UUID) linha[1], (UUID) linha[2], (UUID) linha[3],
                (String) linha[4], (BigDecimal) linha[5], data(linha[6]), null);
    }

    private static LocalDate data(Object valor) {
        return valor instanceof Date sql ? sql.toLocalDate() : (LocalDate) valor;
    }
}
