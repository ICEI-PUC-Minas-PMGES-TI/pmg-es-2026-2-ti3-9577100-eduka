package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;

public interface AlunoResponsavelRepository extends JpaRepository<AlunoResponsavel, AlunoResponsavelId> {

    List<AlunoResponsavel> findAllById_AlunoId(UUID alunoId);

    List<AlunoResponsavel> findAllById_PessoaId(UUID pessoaId);

    Optional<AlunoResponsavel> findById_AlunoIdAndFinanceiroTrue(UUID alunoId);

    boolean existsById_PessoaId(UUID pessoaId);
}
