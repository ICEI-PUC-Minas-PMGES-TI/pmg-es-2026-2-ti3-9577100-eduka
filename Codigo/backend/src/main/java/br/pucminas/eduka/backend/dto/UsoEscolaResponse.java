package br.pucminas.eduka.backend.dto;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Uso de uma escola no Painel de Escolas (IU02).
 *
 * @param alunos          alunos cadastrados
 * @param usuariosAtivos  logins ativos da escola (todos os perfis)
 * @param mes             mes de {@code cobrancasNoMes}, no fuso da escola
 * @param cobrancasNoMes  cobrancas lancadas no mes, canceladas ou nao
 * @param ultimaAtividade ultimo login ou ultima acao registrada de um usuario
 *                        da escola; nulo se nao houve nenhum
 */
public record UsoEscolaResponse(
        UUID escolaId,
        long alunos,
        long usuariosAtivos,
        YearMonth mes,
        long cobrancasNoMes,
        Instant ultimaAtividade) {
}
