package br.pucminas.eduka.backend.repository.pessoas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.pessoas.Aluno;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {

    List<Aluno> findAllByEscolaId(UUID escolaId);

    long countByEscolaId(UUID escolaId);

    /** Alunos que ainda contam na escola: o anonimizado ja saiu e nao e "sem turma". */
    long countByEscolaIdAndAnonimizadoEmIsNull(UUID escolaId);

    Optional<Aluno> findByIdAndEscolaId(UUID id, UUID escolaId);

    /** Aluno dono do login (portal do aluno). */
    Optional<Aluno> findByUsuarioIdAndEscolaId(UUID usuarioId, UUID escolaId);

    boolean existsByEscolaIdAndMatricula(UUID escolaId, String matricula);

    boolean existsByEscolaIdAndMatriculaAndIdNot(UUID escolaId, String matricula, UUID id);
}