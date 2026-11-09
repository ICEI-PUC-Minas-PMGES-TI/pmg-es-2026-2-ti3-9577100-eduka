package br.pucminas.eduka.backend.dto;

import java.util.List;

/**
 * Uma pagina de listagem com o resumo que a tela mostra acima da lista
 * (cards e contadores das abas). Mesmos campos de {@link PaginaResponse},
 * mais o {@code resumo}.
 *
 * <p>
 * O resumo conta a escola inteira, sem a busca nem o filtro de situacao:
 * os numeros nao mudam enquanto o usuario digita ou troca de aba, como nas
 * pilulas de {@link PaginaCobrancasResponse}. Outros filtros (periodo,
 * categoria) valem para o resumo; cada listagem documenta os dela.
 * </p>
 *
 * @param pagina numero da pagina, a partir de 0
 */
public record PaginaComResumoResponse<T, R>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas,
        R resumo) {

    public static <T, R> PaginaComResumoResponse<T, R> de(PaginaResponse<T> pagina, R resumo) {
        return new PaginaComResumoResponse<>(pagina.conteudo(), pagina.pagina(), pagina.tamanho(),
                pagina.totalElementos(), pagina.totalPaginas(), resumo);
    }
}
