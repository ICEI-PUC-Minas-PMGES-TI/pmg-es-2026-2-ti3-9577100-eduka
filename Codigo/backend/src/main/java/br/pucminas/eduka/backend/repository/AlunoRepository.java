package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.Aluno;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {

    List<Aluno> findAllByEscolaId(UUID escolaId);

    Optional<Aluno> findByIdAndEscolaId(UUID id, UUID escolaId);
}