package br.pucminas.eduka.backend.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Filtros da listagem paginada de cobrancas. Todos opcionais; os de data sao
 * inclusivos. {@code periodoLetivoId} vem pela matricula (turma), entao
 * cobranca avulsa, sem matricula, fica de fora quando ele e informado.
 *
 * <p>
 * O filtro de situacao segue {@link StatusCobranca#efetivo}: PENDENTE vencida
 * conta como EM_ATRASO mesmo antes da rotina diaria grava-la assim.
 * </p>
 */
public record CobrancaFiltro(
        UUID alunoId,
        Long periodoLetivoId,
        StatusCobranca status,
        LocalDate vencimentoDe,
        LocalDate vencimentoAte) {

    /** Cobrancas da escola que passam em todos os filtros. */
    public Specification<Cobranca> daEscola(UUID escolaId, LocalDate hoje) {
        return (cobranca, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>(semStatus(escolaId, cobranca, cb));
            if (status != null) {
                condicoes.add(comStatusEfetivo(status, hoje, cobranca, cb));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    /**
     * Como {@link #daEscola}, sem o filtro de situacao: base dos totais por
     * situacao, que mostram todas elas para os mesmos aluno, periodo e
     * vencimento.
     */
    public Specification<Cobranca> daEscolaEmQualquerStatus(UUID escolaId) {
        return (cobranca, query, cb) -> cb.and(semStatus(escolaId, cobranca, cb).toArray(Predicate[]::new));
    }

    /**
     * Cobrancas de algum dos alunos; sem nenhum aluno, nenhuma passa. Para a
     * busca pelo nome, que e cifrado: quem chama acha os alunos fora do banco.
     */
    public static Specification<Cobranca> deAlgumDosAlunos(Collection<UUID> alunoIds) {
        return (cobranca, query, cb) -> alunoIds.isEmpty() ? cb.disjunction()
                : cobranca.get("aluno").get("id").in(alunoIds);
    }

    private List<Predicate> semStatus(UUID escolaId, Root<Cobranca> cobranca, CriteriaBuilder cb) {
        List<Predicate> condicoes = new ArrayList<>();
        condicoes.add(cb.equal(cobranca.get("escolaId"), escolaId));
        if (alunoId != null) {
            condicoes.add(cb.equal(cobranca.get("aluno").get("id"), alunoId));
        }
        if (periodoLetivoId != null) {
            condicoes.add(cb.equal(cobranca.get("matricula").get("turma").get("periodoLetivo").get("id"),
                    periodoLetivoId));
        }
        Path<LocalDate> vencimento = cobranca.get("dataVencimento");
        if (vencimentoDe != null) {
            condicoes.add(cb.greaterThanOrEqualTo(vencimento, vencimentoDe));
        }
        if (vencimentoAte != null) {
            condicoes.add(cb.lessThanOrEqualTo(vencimento, vencimentoAte));
        }
        return condicoes;
    }

    /** Traduz {@link StatusCobranca#efetivo} para SQL. */
    private static Predicate comStatusEfetivo(StatusCobranca status, LocalDate hoje, Root<Cobranca> cobranca,
            CriteriaBuilder cb) {
        Path<StatusCobranca> gravado = cobranca.get("status");
        Path<LocalDate> vencimento = cobranca.get("dataVencimento");
        return switch (status) {
            case PENDENTE -> cb.and(cb.equal(gravado, StatusCobranca.PENDENTE),
                    cb.greaterThanOrEqualTo(vencimento, hoje));
            case EM_ATRASO -> cb.or(cb.equal(gravado, StatusCobranca.EM_ATRASO),
                    cb.and(cb.equal(gravado, StatusCobranca.PENDENTE), cb.lessThan(vencimento, hoje)));
            case PAGA, CANCELADA -> cb.equal(gravado, status);
        };
    }
}
