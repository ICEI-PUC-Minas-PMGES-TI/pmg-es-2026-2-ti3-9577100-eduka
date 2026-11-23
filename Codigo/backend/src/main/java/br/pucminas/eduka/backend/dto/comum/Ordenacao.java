package br.pucminas.eduka.backend.dto.comum;

import java.util.Comparator;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

/**
 * Campos pelos quais cada listagem paginada ordena (parametro
 * {@code ordenarPor}). So os campos daqui: o cliente nao escolhe coluna
 * livre. Sem {@code direcao}, vale a direcao padrao do campo.
 */
public final class Ordenacao {

    private Ordenacao() {
    }

    /** Direcao pedida, ou a padrao do campo. */
    public static Direction ou(Direction pedida, Direction padrao) {
        return pedida != null ? pedida : padrao;
    }

    /** Ordem no banco, com o id desempatando para a pagina nao mudar entre consultas. */
    public static Sort noBanco(Direction direcao, String propriedade) {
        return Sort.by(direcao, propriedade).and(Sort.by(Direction.ASC, "id"));
    }

    /**
     * Ordem em memoria (listagens por nome cifrado), com {@code desempate}
     * fixo depois do campo, pelo mesmo motivo do id em {@link #noBanco}.
     */
    public static <T> Comparator<T> emMemoria(Comparator<T> campo, Direction direcao, Comparator<T> desempate) {
        return (direcao == Direction.DESC ? campo.reversed() : campo).thenComparing(desempate);
    }

    public enum Alunos {
        NOME, MATRICULA;
    }

    public enum Professores {
        NOME, DATA_ADMISSAO;
    }

    public enum Matriculas {
        /** Nome do aluno. */
        ALUNO, DATA_MATRICULA;
    }

    public enum Turmas {
        NOME("nome"), CODIGO("codigo");

        public final String propriedade;

        Turmas(String propriedade) {
            this.propriedade = propriedade;
        }
    }

    public enum Despesas {
        VENCIMENTO("dataVencimento"), VALOR("valor"), DESCRICAO("descricao");

        public final String propriedade;

        Despesas(String propriedade) {
            this.propriedade = propriedade;
        }
    }

    public enum Faturas {
        /** Padrao decrescente: a mais recente primeiro. */
        COMPETENCIA("competencia", Direction.DESC),
        VENCIMENTO("dataVencimento", Direction.ASC),
        VALOR("valor", Direction.ASC);

        public final String propriedade;
        public final Direction padrao;

        Faturas(String propriedade, Direction padrao) {
            this.propriedade = propriedade;
            this.padrao = padrao;
        }
    }
}
