package br.pucminas.eduka.backend.service.privacidade;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.domain.pessoas.Aluno;

/**
 * Politica de retencao dos dados pessoais do aluno (LGPD, RNF05). Ver
 * {@code Artefatos/arquitetura/politica-de-retencao-lgpd.md}.
 *
 * <p>
 * O aluno inativo ha {@code app.lgpd.retencao.anos} anos (5 por padrao) e
 * anonimizado pela {@link RetencaoDadosAlunoRotina}. O prazo conta da
 * inativacao e acompanha o da cobranca de mensalidade (Codigo Civil, art.
 * 206, par. 5, I) e o da guarda de documentos fiscais (CTN, art. 173): depois
 * dele a escola nao precisa mais saber quem era o aluno. O historico
 * financeiro e o escolar ficam, sem nada que o identifique.
 * </p>
 */
@Component
public class PoliticaRetencao {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final int anos;

    public PoliticaRetencao(@Value("${app.lgpd.retencao.anos:5}") int anos) {
        if (anos < 1) {
            throw new IllegalArgumentException("app.lgpd.retencao.anos tem que ser de pelo menos 1 ano");
        }
        this.anos = anos;
    }

    public int anos() {
        return anos;
    }

    /** Quem foi inativado ate este instante ja cumpriu o prazo em {@code agora}. */
    public Instant inativadosAte(Instant agora) {
        return agora.atZone(FUSO_ESCOLA).minusYears(anos).toInstant();
    }

    /**
     * Dia em que a rotina anonimiza o aluno, no fuso da escola. Nulo para o
     * aluno ativo ou ja anonimizado. A rotina ainda adia se, nesse dia, ele
     * tiver matricula vigente ou cobranca em aberto.
     */
    public LocalDate anonimizacaoPrevista(Aluno aluno) {
        if (aluno.isAtivo() || aluno.isAnonimizado() || aluno.getInativadoEm() == null) {
            return null;
        }
        return LocalDate.ofInstant(aluno.getInativadoEm(), FUSO_ESCOLA).plusYears(anos);
    }
}
