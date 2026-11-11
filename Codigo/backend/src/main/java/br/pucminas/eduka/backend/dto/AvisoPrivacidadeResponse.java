package br.pucminas.eduka.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import br.pucminas.eduka.backend.service.AvisoPrivacidade;

/**
 * O aviso de privacidade em vigor e se o usuario do token ja o aceitou.
 *
 * @param aceitoEm quando este usuario aceitou esta versao; nulo enquanto nao
 *                 aceitou, e a tela mostra o aviso
 */
public record AvisoPrivacidadeResponse(
        String versao,
        LocalDate publicadoEm,
        List<AvisoPrivacidade.ItemResumo> resumo,
        List<AvisoPrivacidade.Secao> secoes,
        Instant aceitoEm) {
}
