package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.pucminas.eduka.backend.domain.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;

public interface AlunoResponsavelRepository extends JpaRepository<AlunoResponsavel, AlunoResponsavelId> {

    List<AlunoResponsavel> findAllById_AlunoId(UUID alunoId);

    List<AlunoResponsavel> findAllById_PessoaId(UUID pessoaId);

    Optional<AlunoResponsavel> findById_AlunoIdAndFinanceiroTrue(UUID alunoId);

    boolean existsById_PessoaId(UUID pessoaId);

    /**
     * Linhas (pessoa do aluno, pessoa do responsavel financeiro). Nativo: os
     * avisos da rotina diaria rodam sem usuario autenticado, e em JPQL o
     * {@code @TenantId} de {@code Aluno} filtraria por uma escola que nao
     * existe. Quem chama confere a escola ao carregar as pessoas.
     */
    @Query(value = "SELECT a.pessoa_id, ar.PESSOA_ID FROM ALUNO_RESPONSAVEL ar "
            + "JOIN alunos a ON a.id_aluno = ar.ALUNO_ID "
            + "WHERE ar.FINANCEIRO AND a.pessoa_id IN (:alunoPessoaIds)", nativeQuery = true)
    List<Object[]> buscarFinanceirosPorPessoaDoAluno(@Param("alunoPessoaIds") Collection<UUID> alunoPessoaIds);

    /** Pessoa do aluno -> pessoa do responsavel financeiro dele (so os alunos que tem um). */
    default Map<UUID, UUID> financeiroPorPessoaDoAluno(Collection<UUID> alunoPessoaIds) {
        if (alunoPessoaIds.isEmpty()) {
            return Map.of();
        }
        return buscarFinanceirosPorPessoaDoAluno(alunoPessoaIds).stream()
                .collect(Collectors.toMap(linha -> (UUID) linha[0], linha -> (UUID) linha[1], (a, b) -> a));
    }
}
