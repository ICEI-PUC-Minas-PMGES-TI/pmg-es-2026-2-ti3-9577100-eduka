package br.pucminas.eduka.backend.repository.pessoas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.pessoas.Professor;
import jakarta.persistence.LockModeType;

@Repository
public interface ProfessorRepository extends JpaRepository<Professor, UUID> {

    List<Professor> findAllByEscolaId(UUID escolaId);

    long countByEscolaId(UUID escolaId);

    long countByEscolaIdAndAtivoTrue(UUID escolaId);

    Optional<Professor> findByIdAndEscolaId(UUID id, UUID escolaId);

    /**
     * Mesma busca, travando a linha do professor ate o fim da transacao. Usada
     * ao montar a grade: duas aulas simultaneas do mesmo professor, em turmas
     * diferentes, passam uma de cada vez pela conferencia de sobreposicao.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Professor p where p.id = :id and p.escolaId = :escolaId")
    Optional<Professor> findComLockByIdAndEscolaId(@Param("id") UUID id, @Param("escolaId") UUID escolaId);

    /** O professor por tras de um login: e assim que o portal descobre quem e "eu". */
    Optional<Professor> findByUsuarioIdAndEscolaId(UUID usuarioId, UUID escolaId);
}
