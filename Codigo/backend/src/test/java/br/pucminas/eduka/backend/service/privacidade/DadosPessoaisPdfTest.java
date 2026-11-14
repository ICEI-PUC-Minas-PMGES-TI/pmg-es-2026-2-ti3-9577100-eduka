package br.pucminas.eduka.backend.service.privacidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/**
 * O PDF abre, traz cada dado do aluno formatado no padrao brasileiro, nao fala
 * da plataforma e numera as paginas. Com {@code -Dpdf.saida=arquivo.pdf}, grava o exemplo para olhar.
 */
class DadosPessoaisPdfTest {

    private static final Instant GERADO_EM = Instant.parse("2026-11-08T17:32:00Z");

    private static DadosPessoaisAluno exemplo(int mensalidades) {
        List<DadosPessoaisAluno.Mensalidade> lista = new ArrayList<>();
        IntStream.range(0, mensalidades).forEach(i -> lista.add(new DadosPessoaisAluno.Mensalidade(
                "Mensalidade " + (i % 12 + 1) + "/2026", LocalDate.of(2026, i % 12 + 1, 10),
                new BigDecimal("1250.00"), i == 0 ? "Cancelada" : "Paga", LocalDate.of(2026, i % 12 + 1, 8))));
        return new DadosPessoaisAluno(
                new DadosPessoaisAluno.Escola("Colégio Horizonte", "12.345.678/0001-90", "secretaria@horizonte.edu.br",
                        "3133334444", "Rua das Acácias, 120, Funcionários, Belo Horizonte - MG, CEP 30140-000"),
                GERADO_EM, "Marta Gestora",
                new DadosPessoaisAluno.Pessoal("Ana Souza Lima", "52998224725", LocalDate.of(2010, 3, 14),
                        "ana.souza@email.com", "31987654321", "Feminino",
                        "Rua Ceará, 45, Savassi, Belo Horizonte - MG, CEP 30150-310", "2024-0137",
                        Instant.parse("2024-02-01T12:00:00Z")),
                List.of(new DadosPessoaisAluno.Responsavel("Carla Souza", "Mãe", "11144477735", "carla@email.com",
                        "31999990000", true),
                        new DadosPessoaisAluno.Responsavel("Pedro Lima", "Pai", null, null, "3132221111", false)),
                List.of(new DadosPessoaisAluno.FrequenciaDisciplina("9º A", "Matemática", 40, 3),
                        new DadosPessoaisAluno.FrequenciaDisciplina("9º A", "Português", 36, 0)),
                lista);
    }

    private static String texto(byte[] pdf) throws IOException {
        PdfReader leitor = new PdfReader(pdf);
        try {
            PdfTextExtractor extrator = new PdfTextExtractor(leitor);
            StringBuilder sb = new StringBuilder();
            for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) {
                sb.append(extrator.getTextFromPage(pagina)).append('\n');
            }
            return sb.toString();
        } finally {
            leitor.close();
        }
    }

    @Test
    void trazOsDadosFormatadosEAEscolaComoControladora() throws IOException {
        byte[] pdf = DadosPessoaisPdf.gerar(exemplo(3));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String texto = texto(pdf);
        assertThat(texto).contains("Dados do aluno", "Ana Souza Lima", "529.982.247-25", "14/03/2010",
                "(31) 98765-4321", "Colégio Horizonte", "12.345.678/0001-90", "08/11/2026 às 14:32",
                "Marta Gestora", "Carla Souza", "111.444.777-35", "Boletim", "93%", "Histórico de mensalidades",
                "R$ 1.250,00", "art. 16, I");
        // Documento da escola: nada da plataforma.
        assertThat(texto).doesNotContain("Edúka", "Eduka", "Acesso", "Login");
    }

    @Test
    void numeraAsPaginasComOTotal() throws IOException {
        byte[] pdf = DadosPessoaisPdf.gerar(exemplo(60));
        String saida = System.getProperty("pdf.saida");
        if (saida != null) {
            Files.write(Path.of(saida), pdf);
        }

        PdfReader leitor = new PdfReader(pdf);
        int paginas = leitor.getNumberOfPages();
        leitor.close();
        assertThat(paginas).isGreaterThan(1);
        String texto = texto(pdf);
        assertThat(texto).contains("Página 1 de " + paginas, "Página " + paginas + " de " + paginas);
    }

    @Test
    void secaoSemRegistroDizQueNaoHa() throws IOException {
        DadosPessoaisAluno cheio = exemplo(0);
        DadosPessoaisAluno vazio = new DadosPessoaisAluno(cheio.escola(), GERADO_EM, "Marta Gestora",
                cheio.pessoal(), List.of(), List.of(), List.of());

        assertThat(texto(DadosPessoaisPdf.gerar(vazio))).contains("Nenhum responsável cadastrado.",
                "Nenhum boletim fechado pela secretaria", "Nenhuma chamada registrada.",
                "Nenhuma mensalidade lançada.");
    }

    @Test
    void nomeDoArquivoSemAcentoNemEspaco() {
        assertThat(DadosPessoaisAlunoService.nomeDoArquivo("Ana Souza Lima Ñúñez", GERADO_EM))
                .isEqualTo("dados-do-aluno-ana-souza-lima-nunez-2026-11-08.pdf");
    }
}
