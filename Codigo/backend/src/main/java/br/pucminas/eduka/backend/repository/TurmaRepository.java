package br.pucminas.eduka.backend.repository;

import br.pucminas.eduka.backend.domain.Turma;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurmaRepository extends JpaRepository<Turma, Long>, JpaSpecificationExecutor<Turma> {
    List<Turma> findAllByEscolaId(UUID escolaId);

    /** Listagem paginada, com periodo e curso na mesma consulta. */
    @Override
    @EntityGraph(attributePaths = { "periodoLetivo", "curso" })
    Page<Turma> findAll(Specification<Turma> filtro, Pageable pagina);

    Optional<Turma> findByIdAndEscolaId(Long id, UUID escolaId);

    // Usados antes de excluir periodo, curso ou disciplina.
    boolean existsByEscolaIdAndPeriodoLetivoId(UUID escolaId, Long periodoLetivoId);

    boolean existsByEscolaIdAndCursoId(UUID escolaId, Long cursoId);

    /** A disciplina esta na grade de alguma turma? */
    boolean existsByEscolaIdAndDisciplinas_Id(UUID escolaId, Long disciplinaId);

    /**
     * Mesma busca, travando a linha da turma ate o fim da transacao. Usada ao
     * ocupar vaga: duas matriculas simultaneas na mesma turma passam uma de
     * cada vez pela contagem de vagas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turma t where t.id = :id and t.escola.id = :escolaId")
    Optional<Turma> findComLockByIdAndEscolaId(@Param("id") Long id, @Param("escolaId") UUID escolaId);
}
