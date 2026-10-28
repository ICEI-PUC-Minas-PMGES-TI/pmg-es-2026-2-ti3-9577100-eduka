package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda das regras da arquitetura de pagamento (spec §4.1): so o roteador
 * chama o provedor, so o adaptador conhece o Asaas e o roteador nao abre
 * transacao em volta da chamada.
 */
class ArquiteturaPagamentoTest {

    private static final Path FONTES = Path.of("src/main/java/br/pucminas/eduka/backend");
    private static final Path PAGAMENTO = FONTES.resolve("pagamento");
    private static final Path ASAAS = PAGAMENTO.resolve("asaas");

    @Test
    void soOPacoteDoRoteadorEOAdaptadorUsamOProvedor() {
        List<Path> usam = fontesQueContem("PaymentProvider");

        assertThat(usam)
                .isNotEmpty()
                .allSatisfy(arquivo -> assertThat(arquivo.getParent().equals(PAGAMENTO) || arquivo.startsWith(ASAAS))
                        .as("%s usa o PaymentProvider fora do roteador", arquivo)
                        .isTrue());
    }

    @Test
    void soOAdaptadorImportaTiposDoAsaas() {
        assertThat(fontesQueContem("import br.pucminas.eduka.backend.pagamento.asaas"))
                .allSatisfy(arquivo -> assertThat(arquivo.startsWith(ASAAS))
                        .as("%s importa o pacote do Asaas", arquivo)
                        .isTrue());
    }

    @Test
    void roteadorNaoETransacional() {
        assertThat(PaymentRoutingService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(Stream.of(PaymentRoutingService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Transactional.class))).isEmpty();
    }

    private static List<Path> fontesQueContem(String trecho) {
        try (Stream<Path> arquivos = Files.walk(FONTES)) {
            return arquivos
                    .filter(arquivo -> arquivo.toString().endsWith(".java"))
                    .filter(arquivo -> conteudo(arquivo).contains(trecho))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String conteudo(Path arquivo) {
        try {
            return Files.readString(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
