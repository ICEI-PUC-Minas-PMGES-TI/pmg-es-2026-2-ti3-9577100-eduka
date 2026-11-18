package br.pucminas.eduka.backend.repository.academico;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.domain.academico.HorarioTurma;

@Repository
public interface HorarioTurmaRepository extends JpaRepository<HorarioTurma, UUID> {

    /** A grade da turma, ja com disciplina e professor da alocacao numa query so. */
    @EntityGraph(attributePaths = { "alocacao", "alocacao.disciplina", "alocacao.professor" })
    List<HorarioTurma> findAllByEscolaIdAndAlocacao_Turma_Id(UUID escolaId, Long turmaId);

    @EntityGraph(attributePaths = { "alocacao", "alocacao.disciplina", "alocacao.professor" })
    Optional<HorarioTurma> findByIdAndEscolaIdAndAlocacao_Turma_Id(UUID id, UUID escolaId, Long turmaId);

    /** As aulas das alocacoes do professor, para o horario no portal dele (IU13). */
    List<HorarioTurma> findAllByEscolaIdAndAlocacao_IdIn(UUID escolaId, Collection<UUID> alocacaoIds);

    /** As aulas da turma no dia, para conferir sobreposicao. */
    List<HorarioTurma> findAllByEscolaIdAndAlocacao_Turma_IdAndDiaSemana(UUID escolaId, Long turmaId,
            DiaSemana diaSemana);

    /** As aulas do professor no dia, em qualquer turma, para conferir sobreposicao. */
    List<HorarioTurma> findAllByEscolaIdAndAlocacao_Professor_IdAndDiaSemana(UUID escolaId, UUID professorId,
            DiaSemana diaSemana);
}
