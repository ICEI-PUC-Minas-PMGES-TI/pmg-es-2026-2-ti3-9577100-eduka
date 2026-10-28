package br.pucminas.eduka.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.LogAuditoria;

@Repository
public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, UUID> {

    /** Ultimas acoes da escola, para o painel. Sem {@code @TenantId}: a escola entra explicita. */
    List<LogAuditoria> findTop10ByEscolaIdOrderByDataHoraDesc(UUID escolaId);
}
