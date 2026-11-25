package br.pucminas.eduka.backend.repository.academico;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.academico.Aula;

@Repository
public interface AulaRepository extends JpaRepository<Aula, UUID> {

    /** Pagina das aulas da disciplina na turma, ja com turma e disciplina. */
    @EntityGraph(attributePaths = { "turma", "disciplina" })
    Page<Aula> findAllByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId,
            Pageable pagina);

    /**
     * Pagina das aulas da disciplina na turma entre duas datas (inclusive) e
     * com o conteudo que casa com {@code padrao} (LIKE em minusculas, com
     * {@code \\} de escape; "%" casa com tudo), ja com turma e disciplina.
     */
    @EntityGraph(attributePaths = { "turma", "disciplina" })
    @Query("""
            select a from Aula a
            where a.escolaId = :escolaId and a.turma.id = :turmaId and a.disciplina.id = :disciplinaId
              and a.data between :de and :ate
              and lower(a.conteudo) like :padrao escape '\\'
            """)
    Page<Aula> filtrar(@Param("escolaId") UUID escolaId, @Param("turmaId") Long turmaId,
            @Param("disciplinaId") Long disciplinaId, @Param("de") LocalDate de, @Param("ate") LocalDate ate,
            @Param("padrao") String padrao, Pageable pagina);

    /**
     * As mesmas aulas de {@link #filtrar}, contadas por mes (o mais recente
     * primeiro), com o total de presencas e de faltas das chamadas de cada mes.
     */
    @Query("""
            select year(a.data) as ano, month(a.data) as mes, count(distinct a.id) as aulas,
                   coalesce(sum(case when f.presente = true then 1 else 0 end), 0) as presentes,
                   coalesce(sum(case when f.presente = false then 1 else 0 end), 0) as faltas
            from Aula a left join Frequencia f on f.aula = a
            where a.escolaId = :escolaId and a.turma.id = :turmaId and a.disciplina.id = :disciplinaId
              and a.data between :de and :ate
              and lower(a.conteudo) like :padrao escape '\\'
            group by year(a.data), month(a.data)
            order by year(a.data) desc, month(a.data) desc
            """)
    List<AulasDoMes> contarPorMes(@Param("escolaId") UUID escolaId, @Param("turmaId") Long turmaId,
            @Param("disciplinaId") Long disciplinaId, @Param("de") LocalDate de, @Param("ate") LocalDate ate,
            @Param("padrao") String padrao);

    interface AulasDoMes {
        int getAno();

        int getMes();

        long getAulas();

        long getPresentes();

        long getFaltas();
    }

    /** As aulas da disciplina na turma num dia, ja com turma e disciplina. */
    @EntityGraph(attributePaths = { "turma", "disciplina" })
    List<Aula> findAllByEscolaIdAndTurmaIdAndDisciplinaIdAndData(UUID escolaId, Long turmaId, Long disciplinaId,
            LocalDate data, Sort ordem);

    @EntityGraph(attributePaths = { "turma", "disciplina" })
    Optional<Aula> findByIdAndEscolaId(UUID id, UUID escolaId);

    /** As aulas registradas nas turmas entre duas datas (inclusive), para a semana do professor. */
    @EntityGraph(attributePaths = { "turma", "disciplina" })
    List<Aula> findAllByEscolaIdAndTurmaIdInAndDataBetween(UUID escolaId, Collection<Long> turmaIds, LocalDate de,
            LocalDate ate);

    /** A turma tem aula registrada (a FK da V35 impede apagar a turma). */
    boolean existsByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    /** A disciplina tem aula registrada na turma (a FK da V35 impede tira-la da grade). */
    boolean existsByEscolaIdAndTurmaIdAndDisciplinaId(UUID escolaId, Long turmaId, Long disciplinaId);
}
