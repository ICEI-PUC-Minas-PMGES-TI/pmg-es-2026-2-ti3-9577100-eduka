package br.pucminas.eduka.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.LogAuditoria;

@Repository
public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, UUID>,
        JpaSpecificationExecutor<LogAuditoria> {

    /**
     * Ultimas acoes da escola nas entidades pedidas, para o painel. Sem
     * {@code @TenantId}: a escola entra explicita.
     */
    List<LogAuditoria> findTop10ByEscolaIdAndEntidadeInOrderByDataHoraDesc(UUID escolaId,
            Collection<EntidadeAuditada> entidades);
}
