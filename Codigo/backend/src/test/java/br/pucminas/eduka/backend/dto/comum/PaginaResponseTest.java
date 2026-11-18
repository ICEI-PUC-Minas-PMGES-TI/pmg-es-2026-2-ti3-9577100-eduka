package br.pucminas.eduka.backend.dto.comum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class PaginaResponseTest {

    private static final List<String> CINCO = List.of("a", "b", "c", "d", "e");

    @Test
    void recortaAPaginaPedidaEContaOTotal() {
        assertThat(PaginaResponse.recortar(CINCO, new Paginacao(0, 2)))
                .isEqualTo(new PaginaResponse<>(List.of("a", "b"), 0, 2, 5, 3));
        assertThat(PaginaResponse.recortar(CINCO, new Paginacao(2, 2)))
                .isEqualTo(new PaginaResponse<>(List.of("e"), 2, 2, 5, 3));
    }

    @Test
    void paginaDepoisDoFimVemVaziaComOsTotais() {
        assertThat(PaginaResponse.recortar(CINCO, new Paginacao(7, 2)))
                .isEqualTo(new PaginaResponse<>(List.of(), 7, 2, 5, 3));
        assertThat(PaginaResponse.recortar(List.<String>of(), new Paginacao(0, 20)))
                .isEqualTo(new PaginaResponse<>(List.of(), 0, 20, 0, 0));
    }

    @Test
    void paginaETamanhoForaDosLimitesSaoRecusados() {
        assertThatThrownBy(() -> new Paginacao(-1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paginacao(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paginacao(0, Paginacao.TAMANHO_MAXIMO + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Paginacao(0, Paginacao.TAMANHO_MAXIMO).tamanho()).isEqualTo(Paginacao.TAMANHO_MAXIMO);
    }

    @Test
    void ordemDeNomeIgnoraAcentoEMaiusculaEDeixaNuloNoFim() {
        assertThat(Stream.of("Bruna", null, "Álvaro", "alice", "Zeca").sorted(Paginacao.ORDEM_DE_NOME).toList())
                .containsExactly("alice", "Álvaro", "Bruna", "Zeca", null);
    }
}
