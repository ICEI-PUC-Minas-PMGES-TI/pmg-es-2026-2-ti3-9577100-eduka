package br.pucminas.eduka.backend.dto.comum;

/** Arquivo CSV pronto para baixar. */
public record ArquivoCsv(String nome, byte[] conteudo) {

    /** Conjuntos de dados que podem entrar na exportacao do relatorio (IU19-A). */
    public enum Conjunto {
        COBRANCAS,
        PAGAMENTOS,
        DESPESAS
    }

    /** Separador de colunas. E um nome, nao o caractere: {@code ;} nao viaja bem em query string. */
    public enum Separador {
        PONTO_E_VIRGULA(';'),
        VIRGULA(',');

        private final char caractere;

        Separador(char caractere) {
            this.caractere = caractere;
        }

        public char caractere() {
            return caractere;
        }
    }
}
