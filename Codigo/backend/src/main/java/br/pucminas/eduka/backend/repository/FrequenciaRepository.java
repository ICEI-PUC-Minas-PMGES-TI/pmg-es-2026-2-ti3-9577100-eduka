package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Frequencia;

@Repository
public interface FrequenciaRepository extends JpaRepository<Frequencia, UUID> {

    /** A chamada de uma aula, ja com os alunos. */
    @EntityGraph(attributePaths = { "aluno" })
    List<Frequencia> findAllByEscolaIdAndAulaId(UUID escolaId, UUID aulaId);

    /** Presentes e faltas de varias aulas de uma vez, numa query so. */
    @Query("""
            select f.aula.id as aulaId,
                   sum(case when f.presente = true then 1 else 0 end) as presentes,
                   sum(case when f.presente = false then 1 else 0 end) as faltas
            from Frequencia f
            where f.escolaId = :escolaId and f.aula.id in :aulaIds
            group by f.aula.id
            """)
    List<TotaisDaAula> contarPorAula(@Param("escolaId") UUID escolaId,
            @Param("aulaIds") Collection<UUID> aulaIds);

    /**
     * Aulas com chamada e faltas de um aluno em cada disciplina de uma turma
     * (boletim); disciplina sem aula registrada fica de fora.
     */
    @Query("""
            select a.disciplina.id as disciplinaId,
                   count(f) as aulas,
                   sum(case when f.presente = false then 1 else 0 end) as faltas
            from Frequencia f join f.aula a
            where f.escolaId = :escolaId and f.aluno.id = :alunoId and a.turma.id = :turmaId
            group by a.disciplina.id
            """)
    List<FrequenciaDaDisciplina> resumirPorDisciplina(@Param("escolaId") UUID escolaId,
            @Param("alunoId") UUID alunoId, @Param("turmaId") Long turmaId);

    interface TotaisDaAula {
        UUID getAulaId();

        long getPresentes();

        long getFaltas();
    }

    interface FrequenciaDaDisciplina {
        Long getDisciplinaId();

        long getAulas();

        long getFaltas();
    }
}
