package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;

public interface AlunoResponsavelRepository extends JpaRepository<AlunoResponsavel, AlunoResponsavelId> {

    List<AlunoResponsavel> findAllById_AlunoId(UUID alunoId);
}
