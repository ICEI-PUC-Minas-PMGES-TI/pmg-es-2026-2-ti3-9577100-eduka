package br.pucminas.eduka.backend.service.privacidade;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Desenha o PDF de dados do aluno (LGPD) em A4, como documento da escola:
 * cabecalho com a escola e quem gerou, dados do aluno, responsaveis, boletim,
 * frequencia e historico de mensalidades. Rodape em toda pagina com
 * "Pagina X de Y".
 *
 * <p>
 * Usa a Helvetica padrao do PDF em Cp1252, que cobre os acentos do
 * portugues, entao nao embute fonte. As cores sao lavanda, como as telas.
 * </p>
 */
final class DadosPessoaisPdf {

    private static final Color ROXO = new Color(0x6A, 0x3F, 0xC4);
    private static final Color LAVANDA = new Color(0x96, 0x67, 0xE0);
    private static final Color VEU = new Color(0xF5, 0xEE, 0xFE);
    private static final Color BORDA = new Color(0xE4, 0xD8, 0xF8);
    private static final Color LISTRA = new Color(0xFB, 0xF9, 0xFE);
    private static final Color TINTA = new Color(0x22, 0x1A, 0x33);
    private static final Color APAGADO = new Color(0x5F, 0x58, 0x73);
    private static final Color SUTIL = new Color(0x8F, 0x88, 0xA3);

    private static final float MARGEM = 48;
    private static final float ALTURA_RODAPE = 36;

    private static final Font MARCA = fonte(Font.BOLD, 9, ROXO);
    private static final Font TITULO = fonte(Font.BOLD, 22, TINTA);
    private static final Font SUBTITULO = fonte(Font.NORMAL, 12, APAGADO);
    private static final Font SECAO = fonte(Font.BOLD, 12.5f, ROXO);
    private static final Font ROTULO = fonte(Font.NORMAL, 7.5f, SUTIL);
    private static final Font VALOR = fonte(Font.NORMAL, 10, TINTA);
    private static final Font VALOR_FORTE = fonte(Font.BOLD, 10, TINTA);
    private static final Font CABECALHO = fonte(Font.BOLD, 8, ROXO);
    private static final Font CELULA = fonte(Font.NORMAL, 9, TINTA);
    private static final Font CELULA_APAGADA = fonte(Font.NORMAL, 8, APAGADO);
    private static final Font TEXTO = fonte(Font.NORMAL, 9.5f, APAGADO);
    private static final Font TEXTO_FORTE = fonte(Font.BOLD, 9.5f, TINTA);
    private static final Font VAZIO = fonte(Font.ITALIC, 9.5f, SUTIL);

    private static final String TRACO = "—";

    /** Espaco minimo para comecar uma secao: o titulo e as primeiras linhas, sem ficar sozinho no pe. */
    private static final float ESPACO_PARA_SECAO = 110;

    private final Document documento;
    private final PdfWriter writer;

    private DadosPessoaisPdf(Document documento, PdfWriter writer) {
        this.documento = documento;
        this.writer = writer;
    }

    static byte[] gerar(DadosPessoaisAluno dados) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, MARGEM, MARGEM, MARGEM, MARGEM + ALTURA_RODAPE);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, saida);
            writer.setPageEvent(new Rodape(dados.escola().nome(), dados.pessoal().nome()));
            documento.addTitle("Dados do aluno - " + dados.pessoal().nome());
            documento.addAuthor(dados.escola().nome());
            documento.addCreator(dados.escola().nome());
            documento.open();

            DadosPessoaisPdf pdf = new DadosPessoaisPdf(documento, writer);
            pdf.capa(dados);
            pdf.dadosPessoais(dados.pessoal());
            pdf.responsaveis(dados.responsaveis());
            pdf.boletim();
            pdf.frequencia(dados.frequencias());
            pdf.mensalidades(dados.mensalidades());
            pdf.observacoes(dados.escola());
        } catch (DocumentException e) {
            throw new IllegalStateException("Nao foi possivel gerar o PDF dos dados pessoais", e);
        } finally {
            documento.close();
        }
        return saida.toByteArray();
    }

    // ------------------------------------------------------------------
    // Secoes
    // ------------------------------------------------------------------

    private void capa(DadosPessoaisAluno dados) {
        Paragraph marca = new Paragraph(dados.escola().nome().toUpperCase(), MARCA);
        marca.setSpacingAfter(10);
        documento.add(marca);

        documento.add(new Paragraph("Dados do aluno", TITULO));
        Paragraph quem = new Paragraph(dados.pessoal().nome() + "  ·  Matrícula " + dados.pessoal().matricula(),
                SUBTITULO);
        quem.setSpacingBefore(4);
        quem.setSpacingAfter(16);
        documento.add(quem);

        DadosPessoaisAluno.Escola escola = dados.escola();
        PdfPTable caixa = tabela(new float[] { 1.5f, 1f });
        caixa.addCell(destaque("Escola", escola.nome(), escola.documento()));
        caixa.addCell(destaque("Gerado em", Formatos.dataHora(dados.geradoEm()), "por " + dados.solicitadoPor()));
        caixa.setSpacingAfter(10);
        documento.add(caixa);

        Paragraph intro = new Paragraph(
                "Dados do aluno guardados pela escola nesta data, entregues a pedido do titular ou do "
                        + "responsável legal (art. 18, II, e art. 19 da LGPD).",
                TEXTO);
        intro.setLeading(14);
        documento.add(intro);
    }

    private void dadosPessoais(DadosPessoaisAluno.Pessoal p) {
        secao("Identificação");
        PdfPTable grade = tabela(new float[] { 1, 1, 1 });
        grade.addCell(campo("Nome completo", p.nome(), 2));
        grade.addCell(campo("CPF", Formatos.cpf(p.cpf()), 1));
        grade.addCell(campo("Data de nascimento", Formatos.data(p.dataNascimento()), 1));
        grade.addCell(campo("Gênero", p.genero(), 1));
        grade.addCell(campo("Número de matrícula", p.matricula(), 1));
        grade.addCell(campo("E-mail", p.email(), 2));
        grade.addCell(campo("Telefone", Formatos.telefone(p.telefone()), 1));
        grade.addCell(campo("Endereço", p.endereco(), 2));
        grade.addCell(campo("Cadastrado em", Formatos.data(p.cadastradoEm()), 1));
        documento.add(grade);
    }

    private void responsaveis(List<DadosPessoaisAluno.Responsavel> responsaveis) {
        secao("Responsáveis");
        if (responsaveis.isEmpty()) {
            vazio("Nenhum responsável cadastrado.");
            return;
        }
        PdfPTable t = lista(new float[] { 2f, 1.35f, 1.5f, 2.4f, 1.2f },
                "Nome", "Parentesco", "CPF", "Contato", "Financeiro");
        int linha = 0;
        for (DadosPessoaisAluno.Responsavel r : responsaveis) {
            boolean listra = linha++ % 2 == 1;
            t.addCell(celula(r.nome(), listra));
            t.addCell(celula(r.parentesco(), listra));
            t.addCell(celula(Formatos.cpf(r.cpf()), listra));
            t.addCell(celula(juntarLinhas(r.email(), Formatos.telefone(r.telefone())), listra));
            t.addCell(celula(r.financeiro() ? "Sim" : "Não", listra));
        }
        documento.add(t);
        nota("O responsável financeiro é quem paga as cobranças do aluno.");
    }

    /** O boletim sera o fechado pela secretaria, que ainda nao existe. */
    private void boletim() {
        secao("Boletim");
        vazio("Nenhum boletim fechado pela secretaria até esta data.");
    }

    private void frequencia(List<DadosPessoaisAluno.FrequenciaDisciplina> linhas) {
        secao("Frequência");
        if (linhas.isEmpty()) {
            vazio("Nenhuma chamada registrada.");
            return;
        }
        PdfPTable t = lista(new float[] { 1.3f, 2.6f, 0.9f, 0.9f, 1.1f },
                "Turma", "Disciplina", ">Aulas", ">Faltas", ">Presença");
        int linha = 0;
        for (DadosPessoaisAluno.FrequenciaDisciplina f : linhas) {
            boolean listra = linha++ % 2 == 1;
            t.addCell(celula(f.turma(), listra));
            t.addCell(celula(f.disciplina(), listra));
            t.addCell(numerica(String.valueOf(f.aulas()), listra));
            t.addCell(numerica(String.valueOf(f.faltas()), listra));
            BigDecimal presenca = BigDecimal.valueOf(f.aulas() - f.faltas()).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(f.aulas()), 0, RoundingMode.HALF_UP);
            t.addCell(numerica(presenca + "%", listra));
        }
        documento.add(t);
    }

    private void mensalidades(List<DadosPessoaisAluno.Mensalidade> mensalidades) {
        secao("Histórico de mensalidades");
        if (mensalidades.isEmpty()) {
            vazio("Nenhuma mensalidade lançada.");
            return;
        }
        PdfPTable t = lista(new float[] { 1.1f, 2.8f, 1.2f, 1.1f, 1.1f },
                "Vencimento", "Descrição", ">Valor", "Situação", "Paga em");
        int linha = 0;
        for (DadosPessoaisAluno.Mensalidade c : mensalidades) {
            boolean listra = linha++ % 2 == 1;
            t.addCell(celula(Formatos.data(c.vencimento()), listra));
            t.addCell(celula(c.descricao(), listra));
            t.addCell(numerica(Formatos.moeda(c.valor()), listra));
            t.addCell(celula(c.situacao(), listra));
            t.addCell(celula(Formatos.data(c.pagaEm()), listra));
        }
        documento.add(t);
    }

    private void observacoes(DadosPessoaisAluno.Escola escola) {
        secao("Observações");
        paragrafo("Mensalidades. ", "Se os dados do aluno forem apagados a pedido, o histórico de mensalidades "
                + "continua guardado pela escola, porque a lei exige (art. 16, I, da LGPD).");
        Paragraph correcao = new Paragraph();
        correcao.add(new Chunk("Correção de dados. ", TEXTO_FORTE));
        correcao.add(new Chunk("Para corrigir algum dado, fale com a escola", TEXTO));
        List<String> contatos = Stream.of(escola.email(), Formatos.telefone(escola.telefone()))
                .filter(c -> c != null && !c.isBlank()).toList();
        for (int i = 0; i < contatos.size(); i++) {
            correcao.add(new Chunk(i == 0 ? " pelo " : " ou ", TEXTO));
            // O ponto vai junto do ultimo contato, para nao sobrar sozinho na linha de baixo.
            correcao.add(inteiro(contatos.get(i) + (i == contatos.size() - 1 ? "." : "")));
        }
        if (contatos.isEmpty()) {
            correcao.add(new Chunk(".", TEXTO));
        }
        correcao.setLeading(14);
        correcao.setSpacingAfter(6);
        documento.add(correcao);
        nota("Documento confidencial: contém dados pessoais. Entregue apenas ao titular ou ao responsável "
                + "legal dele.");
    }


    // ------------------------------------------------------------------
    // Blocos
    // ------------------------------------------------------------------

    private void secao(String titulo) {
        if (writer.getVerticalPosition(true) - documento.bottom() < ESPACO_PARA_SECAO) {
            documento.newPage();
        }
        PdfPTable faixa = tabela(new float[] { 1 });
        PdfPCell cell = new PdfPCell(new Phrase(titulo, SECAO));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDA);
        cell.setBorderWidth(1f);
        cell.setPaddingLeft(0);
        cell.setPaddingBottom(6);
        faixa.addCell(cell);
        faixa.setSpacingBefore(20);
        faixa.setSpacingAfter(8);
        faixa.setKeepTogether(true);
        documento.add(faixa);
    }

    /** Bloco da capa: rotulo pequeno, valor em destaque e uma linha de apoio. */
    private static PdfPCell destaque(String rotulo, String valor, String apoio) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(rotulo.toUpperCase(), ROTULO));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk(ou(valor), VALOR_FORTE));
        if (apoio != null) {
            p.add(Chunk.NEWLINE);
            p.add(new Chunk(apoio, CELULA_APAGADA));
        }
        p.setLeading(14);
        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(VEU);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(12);
        cell.setPaddingTop(8);
        return cell;
    }

    /** Par rotulo/valor numa grade de dados. */
    private static PdfPCell campo(String rotulo, String valor, int colunas) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(rotulo.toUpperCase(), ROTULO));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk(ou(valor), VALOR));
        p.setLeading(13);
        PdfPCell cell = new PdfPCell(p);
        cell.setColspan(colunas);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        cell.setPaddingBottom(10);
        cell.setPaddingRight(10);
        return cell;
    }

    /**
     * Tabela de registros, com cabecalho repetido se quebrar de pagina. O
     * cabecalho que comeca com "&gt;" e de coluna numerica: alinha a direita,
     * como os valores.
     */
    private static PdfPTable lista(float[] larguras, String... cabecalhos) {
        PdfPTable t = tabela(larguras);
        for (String cabecalho : cabecalhos) {
            boolean numerica = cabecalho.startsWith(">");
            String texto = numerica ? cabecalho.substring(1) : cabecalho;
            PdfPCell cell = new PdfPCell(new Phrase(texto.toUpperCase(), CABECALHO));
            if (numerica) {
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            cell.setBackgroundColor(VEU);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(LAVANDA);
            cell.setBorderWidth(0.8f);
            cell.setPadding(6);
            cell.setPaddingBottom(7);
            t.addCell(cell);
        }
        t.setHeaderRows(1);
        return t;
    }

    private static PdfPCell celula(String texto, boolean listra) {
        return celula(new Phrase(ou(texto), CELULA), listra);
    }

    private static PdfPCell numerica(String texto, boolean listra) {
        PdfPCell cell = celula(texto, listra);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    /** Valor e, embaixo, um texto menor (observacao). */
    private static PdfPCell celulaComDetalhe(String texto, String detalhe, boolean listra) {
        Phrase frase = new Phrase(ou(texto), CELULA);
        if (detalhe != null && !detalhe.isBlank()) {
            frase.add(Chunk.NEWLINE);
            frase.add(new Chunk(detalhe, CELULA_APAGADA));
        }
        return celula(frase, listra);
    }

    private static PdfPCell celula(Phrase frase, boolean listra) {
        PdfPCell cell = new PdfPCell(frase);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDA);
        cell.setBorderWidth(0.5f);
        cell.setPadding(6);
        cell.setLeading(0, 1.25f);
        if (listra) {
            cell.setBackgroundColor(LISTRA);
        }
        return cell;
    }

    private void vazio(String texto) {
        documento.add(new Paragraph(texto, VAZIO));
    }

    private void nota(String texto) {
        Paragraph p = new Paragraph(texto, CELULA_APAGADA);
        p.setSpacingBefore(6);
        documento.add(p);
    }

    private void paragrafo(String destaque, String texto) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(destaque, TEXTO_FORTE));
        p.add(new Chunk(texto, TEXTO));
        p.setLeading(14);
        p.setSpacingAfter(6);
        documento.add(p);
    }

    /** Texto que nao quebra de linha no meio (telefone, e-mail). */
    private static Chunk inteiro(String texto) {
        Chunk chunk = new Chunk(texto, TEXTO);
        chunk.setSplitCharacter((inicio, atual, fim, letras, chunks) -> false);
        return chunk;
    }

    private static PdfPTable tabela(float[] larguras) {
        PdfPTable t = new PdfPTable(larguras);
        t.setWidthPercentage(100);
        return t;
    }

    private static String ou(String valor) {
        return valor == null || valor.isBlank() ? TRACO : valor;
    }

    private static String juntarLinhas(String... partes) {
        String junto = juntar("\n", partes);
        return junto == null ? TRACO : junto;
    }

    private static String juntar(String separador, String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String parte : partes) {
            if (parte != null && !parte.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(separador);
                }
                sb.append(parte);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static Font fonte(int estilo, float tamanho, Color cor) {
        return new Font(Font.HELVETICA, tamanho, estilo, cor);
    }

    /**
     * Rodape de toda pagina: de quem e o relatorio e "Pagina X de Y". O total
     * so e conhecido ao fechar o documento, entao entra num template.
     */
    private static final class Rodape extends PdfPageEventHelper {

        private final String titulo;
        private final BaseFont fonte;
        private PdfTemplate total;

        Rodape(String escola, String nomeDoAluno) {
            this.titulo = escola + "  ·  Dados do aluno " + nomeDoAluno + "  ·  Confidencial";
            try {
                this.fonte = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = MARGEM - 8;
            float esquerda = document.left();
            float direita = document.right();

            cb.saveState();
            cb.setColorStroke(BORDA);
            cb.setLineWidth(0.6f);
            cb.moveTo(esquerda, y + 14);
            cb.lineTo(direita, y + 14);
            cb.stroke();
            cb.restoreState();

            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(titulo, fonte(Font.NORMAL, 7.5f, SUTIL)), esquerda, y, 0);

            String pagina = "Página " + writer.getPageNumber() + " de ";
            float larguraTexto = fonte.getWidthPoint(pagina, 7.5f);
            cb.beginText();
            cb.setFontAndSize(fonte, 7.5f);
            cb.setColorFill(SUTIL);
            cb.setTextMatrix(direita - 22 - larguraTexto, y);
            cb.showText(pagina);
            cb.endText();
            cb.addTemplate(total, direita - 22, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(fonte, 7.5f);
            total.setColorFill(SUTIL);
            total.setTextMatrix(0, 0);
            // Ao fechar, o numero ja aponta para a pagina seguinte, que nao existe.
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
