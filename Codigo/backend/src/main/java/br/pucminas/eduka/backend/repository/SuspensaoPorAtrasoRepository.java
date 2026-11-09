package br.pucminas.eduka.backend.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import br.pucminas.eduka.backend.domain.FaturaPlataforma;

/**
 * Faturas da plataforma em atraso que levam ao aviso e a suspensao da escola
 * (V43). Nativo: a rotina roda sem usuario, e as colunas do aviso ficam fora
 * da entidade, para um {@code save} da fatura nao desfazer o aviso.
 */
public interface SuspensaoPorAtrasoRepository extends Repository<FaturaPlataforma, UUID> {

    String COLUNAS = "SELECT f.ID_FATURA AS id, f.ESCOLA_ID AS escolaId, f.DESCRICAO AS descricao, "
            + "f.VALOR AS valor, f.DATA_VENCIMENTO AS dataVencimento, "
            + "f.SUSPENSAO_PREVISTA_PARA AS suspensaoPrevistaPara FROM FATURA_PLATAFORMA f "
            + "JOIN ESCOLA e ON e.ID_ESCOLA = f.ESCOLA_ID ";

    String EM_ABERTO = "f.STATUS IN ('PENDENTE', 'EM_ATRASO') ";

    /** Faturas em aberto vencidas ate {@code venceuAte}, ainda sem aviso, de escola ativa. */
    @Query(value = COLUNAS + "WHERE " + EM_ABERTO + "AND f.DATA_VENCIMENTO <= :venceuAte "
            + "AND f.AVISO_SUSPENSAO_EM IS NULL AND e.ATIVO ORDER BY f.DATA_VENCIMENTO", nativeQuery = true)
    List<FaturaEmAtraso> paraAvisar(@Param("venceuAte") LocalDate venceuAte);

    /** Faturas em aberto com a suspensao prevista ate {@code hoje}, de escola ativa. */
    @Query(value = COLUNAS + "WHERE " + EM_ABERTO + "AND f.SUSPENSAO_PREVISTA_PARA <= :hoje AND e.ATIVO "
            + "ORDER BY f.SUSPENSAO_PREVISTA_PARA", nativeQuery = true)
    List<FaturaEmAtraso> paraSuspender(@Param("hoje") LocalDate hoje);

    /** A suspensao mais proxima da escola, para o aviso na tela do MASTER. */
    @Query(value = COLUNAS + "WHERE " + EM_ABERTO + "AND f.ESCOLA_ID = :escolaId "
            + "AND f.SUSPENSAO_PREVISTA_PARA IS NOT NULL ORDER BY f.SUSPENSAO_PREVISTA_PARA LIMIT 1",
            nativeQuery = true)
    Optional<FaturaEmAtraso> suspensaoPrevista(@Param("escolaId") UUID escolaId);

    /** @return 1 se gravou; 0 se outra execucao ja avisou */
    @Modifying
    @Query(value = "UPDATE FATURA_PLATAFORMA SET AVISO_SUSPENSAO_EM = :agora, SUSPENSAO_PREVISTA_PARA = :prevista "
            + "WHERE ID_FATURA = :id AND AVISO_SUSPENSAO_EM IS NULL", nativeQuery = true)
    int marcarAviso(@Param("id") UUID id, @Param("agora") Instant agora, @Param("prevista") LocalDate prevista);

    interface FaturaEmAtraso {
        UUID getId();

        UUID getEscolaId();

        String getDescricao();

        BigDecimal getValor();

        LocalDate getDataVencimento();

        /** Nula enquanto nao avisada. */
        LocalDate getSuspensaoPrevistaPara();
    }
}
