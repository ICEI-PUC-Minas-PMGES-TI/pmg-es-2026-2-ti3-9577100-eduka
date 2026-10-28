package br.pucminas.eduka.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Confere os nomes das migrations sem precisar de banco. O Flyway so descobre
 * versao repetida ao subir a aplicacao contra o Postgres, e os demais testes
 * nao sobem o Flyway: duas branches criando a mesma versao em paralelo (ja
 * aconteceu com V22 e V25) passavam por todos os testes e derrubavam a
 * aplicacao.
 */
class MigracoesFlywayTest {

    /** {@code V<versao>__<descricao>.sql}; a versao aceita {@code 1}, {@code 1.1} ou {@code 1_1}. */
    private static final Pattern NOME = Pattern.compile("V(\\d+(?:[._]\\d+)*)__\\w[\\w-]*\\.sql");

    private static List<String> arquivos() throws IOException {
        Resource[] recursos = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql");
        return Arrays.stream(recursos).map(Resource::getFilename).filter(Objects::nonNull).sorted().toList();
    }

    @Test
    void todoArquivoSegueOPadraoDoFlyway() throws IOException {
        // Fora do padrao (ex.: V30_criar.sql, com um "_" so), o Flyway ignora o arquivo sem avisar.
        assertThat(arquivos()).isNotEmpty().allSatisfy(nome -> assertThat(nome).matches(NOME));
    }

    @Test
    void nenhumaVersaoSeRepete() throws IOException {
        Map<String, List<String>> porVersao = arquivos().stream()
                .map(NOME::matcher)
                .filter(Matcher::matches)
                .collect(Collectors.groupingBy(m -> versao(m.group(1)),
                        Collectors.mapping(Matcher::group, Collectors.toList())));

        Map<String, List<String>> repetidas = porVersao.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(repetidas)
                .as("Versao de migration repetida: renomeie a mais nova para a proxima versao livre")
                .isEmpty();
    }

    /**
     * Forma canonica da versao, como o Flyway compara: "1.1" = "1_1", "01" = "1"
     * e "1.0" = "1".
     */
    private static String versao(String texto) {
        List<String> partes = new ArrayList<>();
        for (String parte : texto.split("[._]")) {
            partes.add(parte.replaceFirst("^0+(?=\\d)", ""));
        }
        while (partes.size() > 1 && partes.getLast().equals("0")) {
            partes.removeLast();
        }
        return String.join(".", partes);
    }
}
