package br.pucminas.eduka.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.AceitePrivacidade;

@Repository
public interface AceitePrivacidadeRepository extends JpaRepository<AceitePrivacidade, UUID> {

    Optional<AceitePrivacidade> findByUsuarioIdAndVersao(UUID usuarioId, String versao);
}
