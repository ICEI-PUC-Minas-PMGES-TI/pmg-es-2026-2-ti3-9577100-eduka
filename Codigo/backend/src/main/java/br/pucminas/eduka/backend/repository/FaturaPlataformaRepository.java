package br.pucminas.eduka.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.FaturaPlataforma;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import jakarta.persistence.LockModeType;

/** Sem {@code @TenantId} na entidade: a escola entra explicita nas consultas da escola. */
@Repository
public interface FaturaPlataformaRepository extends JpaRepository<FaturaPlataforma, UUID> {

    List<FaturaPlataforma> findAllByEscolaIdOrderByCompetenciaDesc(UUID escolaId);

    /** Faturas de todas as escolas (IU24), do vencimento mais recente para o mais antigo. */
    List<FaturaPlataforma> findAllByOrderByDataVencimentoDescCompetenciaDesc();

    Optional<FaturaPlataforma> findByIdAndEscolaId(UUID id, UUID escolaId);

    /** R26: uma fatura por competencia, fora as canceladas. */
    boolean existsByEscolaIdAndCompetenciaAndStatusNot(UUID escolaId, LocalDate competencia, StatusCobranca status);

    /** Trava a fatura enquanto um aviso do provedor e aplicado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FaturaPlataforma f WHERE f.id = :id")
    Optional<FaturaPlataforma> travar(@Param("id") UUID id);

    /** Rotina diaria (spec §7.4): mesma regra de atraso da cobranca. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE FaturaPlataforma f SET f.status = br.pucminas.eduka.backend.domain.StatusCobranca.EM_ATRASO, "
            + "f.atualizadoEm = :agora WHERE f.status = br.pucminas.eduka.backend.domain.StatusCobranca.PENDENTE "
            + "AND f.dataVencimento < :hoje")
    int marcarVencidasComoEmAtraso(@Param("hoje") LocalDate hoje, @Param("agora") Instant agora);

    /** Faturas que ficaram EMITINDO desde antes de {@code iniciadaAntes}, para a reconciliacao (spec §8.4). */
    @Query("SELECT f.id FROM FaturaPlataforma f "
            + "WHERE f.situacaoProvedor = br.pucminas.eduka.backend.domain.SituacaoProvedor.EMITINDO "
            + "AND f.emissaoIniciadaEm < :iniciadaAntes ORDER BY f.emissaoIniciadaEm")
    List<UUID> buscarEmissoesSemResposta(@Param("iniciadaAntes") Instant iniciadaAntes, Pageable pagina);

    /**
     * Faturas EMITIDAS ainda em aberto, para a reconciliacao conferir o
     * pagamento no provedor: a conta-pai pode nao ter webhook.
     */
    @Query("SELECT f FROM FaturaPlataforma f "
            + "WHERE f.situacaoProvedor = br.pucminas.eduka.backend.domain.SituacaoProvedor.EMITIDA "
            + "AND f.status IN (br.pucminas.eduka.backend.domain.StatusCobranca.PENDENTE, "
            + "br.pucminas.eduka.backend.domain.StatusCobranca.EM_ATRASO) ORDER BY f.emitidaEm")
    List<FaturaPlataforma> buscarEmitidasEmAberto(Pageable pagina);
}
