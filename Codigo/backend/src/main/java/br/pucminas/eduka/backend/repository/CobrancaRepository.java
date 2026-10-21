package br.pucminas.eduka.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.StatusCobranca;

@Repository
public interface CobrancaRepository
        extends JpaRepository<Cobranca, UUID>, JpaSpecificationExecutor<Cobranca>, CobrancaTotaisRepository {

    // Os EntityGraph trazem aluno e matricula (com a turma) numa query so,
    // para montar a resposta sem N+1.

    @EntityGraph(attributePaths = { "aluno", "matricula", "matricula.turma" })
    List<Cobranca> findAllByEscolaId(UUID escolaId);

    /** Listagem paginada: os filtros vem de {@link CobrancaFiltro}. */
    @Override
    @EntityGraph(attributePaths = { "aluno", "matricula", "matricula.turma" })
    Page<Cobranca> findAll(Specification<Cobranca> filtro, Pageable pageable);

    @EntityGraph(attributePaths = { "aluno", "matricula", "matricula.turma" })
    List<Cobranca> findAllByEscolaIdAndAlunoId(UUID escolaId, UUID alunoId);

    @EntityGraph(attributePaths = { "aluno", "matricula", "matricula.turma" })
    Optional<Cobranca> findByIdAndEscolaId(UUID id, UUID escolaId);

    /** {@code responsavelId} e o id da pessoa do responsavel, como na coluna. */
    boolean existsByEscolaIdAndResponsavelId(UUID escolaId, UUID responsavelId);

    boolean existsByEscolaIdAndAluno_IdAndResponsavelIdAndStatusIn(UUID escolaId, UUID alunoId,
            UUID responsavelId, Collection<StatusCobranca> status);

    /**
     * Passa para EM_ATRASO as pendentes com vencimento anterior a {@code hoje},
     * de todas as escolas, e devolve quantas mudaram. SQL nativo de proposito:
     * a rotina roda sem usuario autenticado, e em JPQL o {@code @TenantId}
     * filtraria por uma escola que nao existe.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE COBRANCA SET STATUS = 'EM_ATRASO', ATUALIZADO_EM = :agora "
            + "WHERE STATUS = 'PENDENTE' AND DATA_VENCIMENTO < :hoje", nativeQuery = true)
    int marcarVencidasComoEmAtraso(@Param("hoje") LocalDate hoje, @Param("agora") Instant agora);

    /**
     * Pendentes de todas as escolas que vencem entre {@code hoje} e
     * {@code limite} (inclusive) e ainda nao receberam o lembrete. Nativo
     * pelo mesmo motivo de {@link #marcarVencidasComoEmAtraso}. Colunas na
     * ordem de {@link br.pucminas.eduka.backend.service.CobrancaParaAviso#deLinha}.
     */
    @Query(value = "SELECT c.ID_COBRANCA, c.ESCOLA_ID, a.pessoa_id, c.RESPONSAVEL_ID, c.DESCRICAO, c.VALOR, "
            + "c.DATA_VENCIMENTO FROM COBRANCA c JOIN alunos a ON a.id_aluno = c.ALUNO_ID "
            + "WHERE c.STATUS = 'PENDENTE' AND c.LEMBRETE_VENCIMENTO_EM IS NULL "
            + "AND c.DATA_VENCIMENTO BETWEEN :hoje AND :limite", nativeQuery = true)
    List<Object[]> buscarParaLembreteDeVencimento(@Param("hoje") LocalDate hoje, @Param("limite") LocalDate limite);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE COBRANCA SET LEMBRETE_VENCIMENTO_EM = :agora WHERE ID_COBRANCA IN (:ids)",
            nativeQuery = true)
    int marcarLembreteDeVencimentoEnviado(@Param("ids") Collection<UUID> ids, @Param("agora") Instant agora);

    // ---- Pagamento confirmado pelo provedor (webhook) ----
    // Nativos pelo mesmo motivo de marcarVencidasComoEmAtraso: o webhook chega
    // sem usuario autenticado. Colunas na ordem de CobrancaParaAviso#deLinha,
    // seguidas de STATUS e FORMA_PAGAMENTO.

    String COLUNAS_PAGAMENTO_ONLINE = "SELECT c.ID_COBRANCA, c.ESCOLA_ID, a.pessoa_id, c.RESPONSAVEL_ID, "
            + "c.DESCRICAO, c.VALOR, c.DATA_VENCIMENTO, c.STATUS, c.FORMA_PAGAMENTO "
            + "FROM COBRANCA c JOIN alunos a ON a.id_aluno = c.ALUNO_ID ";

    @Query(value = COLUNAS_PAGAMENTO_ONLINE + "WHERE c.ID_COBRANCA_EXTERNA = :idExterno", nativeQuery = true)
    List<Object[]> buscarParaPagamentoOnlinePorIdExterno(@Param("idExterno") String idExterno);

    @Query(value = COLUNAS_PAGAMENTO_ONLINE + "WHERE c.ID_COBRANCA = :id", nativeQuery = true)
    List<Object[]> buscarParaPagamentoOnlinePorId(@Param("id") UUID id);

    /**
     * Marca como PAGA a cobranca ainda em aberto. Devolve 0 se ela ja estava
     * paga ou cancelada: a condicao no WHERE evita corrida com a baixa manual.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE COBRANCA SET STATUS = 'PAGA', PAGA_EM = :agora, ATUALIZADO_EM = :agora, "
            + "VERSAO = VERSAO + 1 WHERE ID_COBRANCA = :id AND STATUS IN ('PENDENTE', 'EM_ATRASO')",
            nativeQuery = true)
    int marcarPagaPeloProvedor(@Param("id") UUID id, @Param("agora") Instant agora);
}
