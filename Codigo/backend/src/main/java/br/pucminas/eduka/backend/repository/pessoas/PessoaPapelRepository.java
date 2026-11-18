package br.pucminas.eduka.backend.repository.pessoas;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.pessoas.PessoaPapel;

public interface PessoaPapelRepository extends JpaRepository<PessoaPapel, UUID> {

    List<PessoaPapel> findAllByPessoaId(UUID pessoaId);
}
