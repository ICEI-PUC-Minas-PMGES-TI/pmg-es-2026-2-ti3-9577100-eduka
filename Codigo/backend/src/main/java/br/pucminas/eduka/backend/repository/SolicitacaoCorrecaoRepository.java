package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.pucminas.eduka.backend.domain.SolicitacaoCorrecao;
import br.pucminas.eduka.backend.domain.StatusSolicitacaoCorrecao;

public interface SolicitacaoCorrecaoRepository extends JpaRepository<SolicitacaoCorrecao, UUID> {

    Optional<SolicitacaoCorrecao> findFirstByEscolaIdAndAlunoIdAndStatus(UUID escolaId, UUID alunoId,
            StatusSolicitacaoCorrecao status);

    /** As mais antigas primeiro: quem pediu antes e atendido antes. */
    List<SolicitacaoCorrecao> findAllByEscolaIdAndStatusOrderByCriadoEmAsc(UUID escolaId,
            StatusSolicitacaoCorrecao status);

    Optional<SolicitacaoCorrecao> findByIdAndEscolaId(UUID id, UUID escolaId);
}
