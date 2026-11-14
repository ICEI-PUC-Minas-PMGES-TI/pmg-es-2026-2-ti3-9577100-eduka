package br.pucminas.eduka.backend.dto.comum;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Uma pagina de qualquer listagem paginada. Mesmos campos da
 * {@link PaginaCobrancasResponse}, que acrescenta so os totais por situacao.
 *
 * @param pagina numero da pagina, a partir de 0
 */
public record PaginaResponse<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas) {

    /** Pagina que o banco ja recortou. */
    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<? super E, T> converter) {
        return new PaginaResponse<>(pagina.getContent().stream().map(converter).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    /**
     * Recorta em memoria uma lista ja filtrada e ordenada. So para listagens
     * que filtram ou ordenam por dado cifrado (nome de pessoa), que o banco
     * nao compara.
     */
    public static <T> PaginaResponse<T> recortar(List<T> ordenados, Paginacao paginacao) {
        int total = ordenados.size();
        int inicio = (int) Math.min((long) paginacao.pagina() * paginacao.tamanho(), total);
        int fim = Math.min(inicio + paginacao.tamanho(), total);
        int totalPaginas = (total + paginacao.tamanho() - 1) / paginacao.tamanho();
        return new PaginaResponse<>(List.copyOf(ordenados.subList(inicio, fim)), paginacao.pagina(),
                paginacao.tamanho(), total, totalPaginas);
    }
}
