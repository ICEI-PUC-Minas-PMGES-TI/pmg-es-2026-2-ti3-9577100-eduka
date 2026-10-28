package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import br.pucminas.eduka.backend.domain.StatusCobranca;

/**
 * Uma pagina da listagem de cobrancas.
 *
 * @param pagina          numero da pagina, a partir de 0
 * @param totaisPorStatus uma entrada por situacao, sempre todas e na ordem de
 *                        {@link StatusCobranca}, calculadas com os mesmos
 *                        filtros da listagem exceto o de situacao, para a
 *                        tela mostrar o tamanho de cada aba
 */
public record PaginaCobrancasResponse(
        List<CobrancaResponse> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas,
        List<TotalPorStatus> totaisPorStatus) {

    /** Situacao efetiva: PENDENTE vencida conta como EM_ATRASO. */
    public record TotalPorStatus(StatusCobranca status, long quantidade, BigDecimal valor) {
    }
}
