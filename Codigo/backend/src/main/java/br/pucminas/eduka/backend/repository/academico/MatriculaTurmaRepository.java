package br.pucminas.eduka.backend.repository.academico;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;

@Repository
public interface MatriculaTurmaRepository extends JpaRepository<MatriculaTurma, UUID> {

    // Os EntityGraph trazem aluno e turma (com periodo e curso) numa query so,
    // para montar a resposta sem N+1.

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    List<MatriculaTurma> findAllByEscolaId(UUID escolaId);

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    List<MatriculaTurma> findAllByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    List<MatriculaTurma> findAllByEscolaIdAndAlunoId(UUID escolaId, UUID alunoId);

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    List<MatriculaTurma> findAllByEscolaIdAndAlunoIdAndStatus(UUID escolaId, UUID alunoId, StatusMatricula status);

    @EntityGraph(attributePaths = { "aluno", "turma", "turma.periodoLetivo", "turma.curso" })
    Optional<MatriculaTurma> findByIdAndEscolaId(UUID id, UUID escolaId);

    /** A turma tem alguma matricula, em qualquer situacao? */
    boolean existsByEscolaIdAndTurmaId(UUID escolaId, Long turmaId);

    boolean existsByEscolaIdAndAlunoIdAndTurmaId(UUID escolaId, UUID alunoId, Long turmaId);

    boolean existsByEscolaIdAndAlunoIdAndStatus(UUID escolaId, UUID alunoId, StatusMatricula status);

    /** Vagas ocupadas: so matricula ATIVA conta. */
    long countByEscolaIdAndTurmaIdAndStatus(UUID escolaId, Long turmaId, StatusMatricula status);

    // ---- Dashboards: contagens no banco, sem carregar o historico de matriculas ----

    /** Uma linha por situacao que tem matricula; as que nao tem ficam de fora. */
    @Query("SELECT m.status AS status, COUNT(m) AS quantidade FROM MatriculaTurma m "
            + "WHERE m.escolaId = :escolaId GROUP BY m.status")
    List<PorSituacao> contarPorSituacao(@Param("escolaId") UUID escolaId);

    long countByEscolaIdAndDataMatriculaGreaterThanEqual(UUID escolaId, LocalDate desde);

    @Query("SELECT COUNT(DISTINCT m.aluno.id) FROM MatriculaTurma m WHERE m.escolaId = :escolaId "
            + "AND m.status = br.pucminas.eduka.backend.domain.academico.StatusMatricula.ATIVA")
    long contarAlunosComMatriculaAtiva(@Param("escolaId") UUID escolaId);

    /** Matriculas ATIVAS de cada turma; turma sem nenhuma fica de fora. */
    @Query("SELECT m.turma.id AS turmaId, COUNT(m) AS quantidade FROM MatriculaTurma m WHERE m.escolaId = :escolaId "
            + "AND m.status = br.pucminas.eduka.backend.domain.academico.StatusMatricula.ATIVA GROUP BY m.turma.id")
    List<AtivasNaTurma> contarAtivasPorTurma(@Param("escolaId") UUID escolaId);

    /**
     * Matriculas ATIVAS e TRANCADAS da escola: dizem em que turma cada aluno
     * esta hoje, sem ler as canceladas e concluidas de periodos passados.
     */
    @Query("SELECT m.aluno.id AS alunoId, m.status AS status, m.turma.id AS turmaId, m.turma.nome AS turmaNome "
            + "FROM MatriculaTurma m WHERE m.escolaId = :escolaId AND m.status IN ("
            + "br.pucminas.eduka.backend.domain.academico.StatusMatricula.ATIVA, "
            + "br.pucminas.eduka.backend.domain.academico.StatusMatricula.TRANCADA)")
    List<Vigente> listarVigentes(@Param("escolaId") UUID escolaId);

    interface Vigente {
        UUID getAlunoId();

        StatusMatricula getStatus();

        Long getTurmaId();

        String getTurmaNome();
    }

    interface PorSituacao {
        StatusMatricula getStatus();

        long getQuantidade();
    }

    interface AtivasNaTurma {
        Long getTurmaId();

        long getQuantidade();
    }
}
