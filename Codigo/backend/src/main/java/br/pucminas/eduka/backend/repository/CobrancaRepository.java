package br.pucminas.eduka.backend.repository;

import java.math.BigDecimal;
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
import br.pucminas.eduka.backend.domain.TipoCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

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

    /**
     * Cobrancas da escola pagas de {@code desde} (inclusive) ate {@code antes}
     * (exclusive), com o que entrou: o valor pago no provedor ou, na baixa
     * manual (sem pagamento registrado), o da cobranca. Para o painel somar
     * por mes.
     */
    @Query("SELECT c.pagaEm AS pagaEm, COALESCE(p.valorPago, c.valor) AS valor FROM Cobranca c "
            + "LEFT JOIN PagamentoCobranca p ON p.cobrancaId = c.id AND p.escolaId = c.escolaId "
            + "WHERE c.escolaId = :escolaId AND c.status = br.pucminas.eduka.backend.domain.StatusCobranca.PAGA "
            + "AND c.pagaEm >= :desde AND c.pagaEm < :antes")
    List<Movimento> recebidosEntre(@Param("escolaId") UUID escolaId, @Param("desde") Instant desde,
            @Param("antes") Instant antes);

    // ---- Relatorios financeiros: contas no banco, sem carregar as cobrancas ----

    /**
     * O que entrou de {@code desde} (inclusive) ate {@code antes} (exclusive),
     * como {@link #recebidosEntre}, com a forma e o que identifica cada
     * pagamento. Do mais antigo ao mais recente. O que foi devolvido depois vem
     * de {@link EstornoCobrancaRepository#estornosEntre}.
     */
    @Query("SELECT c.pagaEm AS pagaEm, COALESCE(p.valorPago, c.valor) AS valor, p.formaPagamento AS forma, "
            + "c.descricao AS descricao, c.aluno.pessoaId AS pessoaId FROM Cobranca c "
            + "LEFT JOIN PagamentoCobranca p ON p.cobrancaId = c.id AND p.escolaId = c.escolaId "
            + "WHERE c.escolaId = :escolaId AND c.status = br.pucminas.eduka.backend.domain.StatusCobranca.PAGA "
            + "AND c.pagaEm >= :desde AND c.pagaEm < :antes ORDER BY c.pagaEm, c.id")
    List<Recebimento> recebimentosEntre(@Param("escolaId") UUID escolaId, @Param("desde") Instant desde,
            @Param("antes") Instant antes);

    /**
     * Totais das cobrancas nao canceladas que vencem de {@code de} a
     * {@code ate} (inclusive). Situacao e vencimento entram no grupo para o
     * servico chegar a situacao efetiva ({@link StatusCobranca#efetivo}); tipo
     * e curso, para somar cada resumo sem outra consulta.
     */
    @Query("SELECT c.tipo AS tipo, curso.id AS cursoId, curso.nome AS cursoNome, c.status AS status, "
            + "c.dataVencimento AS vencimento, COUNT(c) AS quantidade, SUM(c.valor) AS valor, "
            + "SUM(COALESCE(p.valorPago, c.valor) - COALESCE(p.valorEstornado, 0)) AS valorPago FROM Cobranca c "
            + "LEFT JOIN c.matricula matricula LEFT JOIN matricula.turma turma LEFT JOIN turma.curso curso "
            + "LEFT JOIN PagamentoCobranca p ON p.cobrancaId = c.id AND p.escolaId = c.escolaId "
            + "WHERE c.escolaId = :escolaId AND c.status <> br.pucminas.eduka.backend.domain.StatusCobranca.CANCELADA "
            + "AND c.dataVencimento BETWEEN :de AND :ate "
            + "GROUP BY c.tipo, curso.id, curso.nome, c.status, c.dataVencimento")
    List<CobrancasDoGrupo> totaisPorVencimento(@Param("escolaId") UUID escolaId, @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);

    /** As cobrancas de {@link #totaisPorVencimento}, uma a uma, para a exportacao. */
    @EntityGraph(attributePaths = { "aluno", "matricula", "matricula.turma", "matricula.turma.curso" })
    @Query("SELECT c FROM Cobranca c WHERE c.escolaId = :escolaId "
            + "AND c.status <> br.pucminas.eduka.backend.domain.StatusCobranca.CANCELADA "
            + "AND c.dataVencimento BETWEEN :de AND :ate ORDER BY c.dataVencimento, c.id")
    List<Cobranca> naoCanceladasQueVencemEntre(@Param("escolaId") UUID escolaId, @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);

    /** Dinheiro que entrou (valor positivo) ou que foi devolvido num estorno (negativo). */
    interface Recebimento extends Movimento {
        /** PIX ou BOLETO; nula na baixa manual. */
        FormaPagamento getForma();

        String getDescricao();

        UUID getPessoaId();
    }

    interface CobrancasDoGrupo {
        TipoCobranca getTipo();

        /** Nulo na cobranca avulsa, sem matricula. */
        Long getCursoId();

        String getCursoNome();

        StatusCobranca getStatus();

        LocalDate getVencimento();

        long getQuantidade();

        BigDecimal getValor();

        /**
         * O pago no provedor sem o estornado ou, sem pagamento registrado, o da
         * cobranca. So tem sentido na PAGA.
         */
        BigDecimal getValorPago();
    }

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

    // ---- Avisos do provedor (webhook) ----
    // Nativos pelo mesmo motivo de marcarVencidasComoEmAtraso: o webhook chega
    // sem usuario autenticado. Colunas na ordem de CobrancaParaAviso#deLinha,
    // seguidas de STATUS, FORMA_PAGAMENTO e SITUACAO_PROVEDOR.

    String COLUNAS_PAGAMENTO_ONLINE = "SELECT c.ID_COBRANCA, c.ESCOLA_ID, a.pessoa_id, c.RESPONSAVEL_ID, "
            + "c.DESCRICAO, c.VALOR, c.DATA_VENCIMENTO, c.STATUS, c.FORMA_PAGAMENTO, c.SITUACAO_PROVEDOR "
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

    /** O provedor recebeu o pagamento de uma cobranca ja cancelada: o dinheiro prevalece (R23). */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE COBRANCA SET STATUS = 'PAGA', PAGA_EM = :agora, ATUALIZADO_EM = :agora, "
            + "VERSAO = VERSAO + 1 WHERE ID_COBRANCA = :id AND STATUS = 'CANCELADA'", nativeQuery = true)
    int marcarPagaAposCancelamento(@Param("id") UUID id, @Param("agora") Instant agora);

    /** EMITINDO → EMITIDA com o id do provedor, quando o aviso chega antes da resposta da criacao. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE COBRANCA SET SITUACAO_PROVEDOR = 'EMITIDA', ID_COBRANCA_EXTERNA = :idExterno, "
            + "EMITIDA_EM = :agora, ULTIMO_ERRO_PROVEDOR = NULL, ATUALIZADO_EM = :agora, VERSAO = VERSAO + 1 "
            + "WHERE ID_COBRANCA = :id AND SITUACAO_PROVEDOR = 'EMITINDO'", nativeQuery = true)
    int marcarEmitidaPeloProvedor(@Param("id") UUID id, @Param("idExterno") String idExterno,
            @Param("agora") Instant agora);

    /** EMITINDO → FALHA_EMISSAO: a cobranca foi removida no provedor antes de terminar de ser emitida. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE COBRANCA SET SITUACAO_PROVEDOR = 'FALHA_EMISSAO', ULTIMO_ERRO_PROVEDOR = :motivo, "
            + "ATUALIZADO_EM = :agora, VERSAO = VERSAO + 1 "
            + "WHERE ID_COBRANCA = :id AND SITUACAO_PROVEDOR = 'EMITINDO'", nativeQuery = true)
    int marcarFalhaEmissaoPeloProvedor(@Param("id") UUID id, @Param("motivo") String motivo,
            @Param("agora") Instant agora);

    /** Cobranca emitida e em aberto removida no provedor: fica CANCELADA. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE COBRANCA SET STATUS = 'CANCELADA', CANCELADA_EM = :agora, ATUALIZADO_EM = :agora, "
            + "VERSAO = VERSAO + 1 WHERE ID_COBRANCA = :id AND SITUACAO_PROVEDOR = 'EMITIDA' "
            + "AND STATUS IN ('PENDENTE', 'EM_ATRASO')", nativeQuery = true)
    int cancelarPeloProvedor(@Param("id") UUID id, @Param("agora") Instant agora);

    /**
     * Cobrancas que ficaram EMITINDO desde antes de {@code iniciadaAntes}, as
     * mais antigas primeiro, com a credencial da conta da escola, para a
     * reconciliacao (spec §8.4). Nativo: a rotina roda sem usuario.
     */
    @Query(value = "SELECT c.ID_COBRANCA AS id, conta.CREDENCIAL_CIFRADA AS credencialCifrada FROM COBRANCA c "
            + "JOIN CONTA_RECEBIMENTO_ESCOLA conta ON conta.ESCOLA_ID = c.ESCOLA_ID "
            + "WHERE c.SITUACAO_PROVEDOR = 'EMITINDO' AND c.EMISSAO_INICIADA_EM < :iniciadaAntes "
            + "AND conta.CREDENCIAL_CIFRADA IS NOT NULL ORDER BY c.EMISSAO_INICIADA_EM LIMIT :lote",
            nativeQuery = true)
    List<EmissaoSemResposta> buscarEmissoesSemResposta(@Param("iniciadaAntes") Instant iniciadaAntes,
            @Param("lote") int lote);

    interface EmissaoSemResposta {
        UUID getId();

        byte[] getCredencialCifrada();
    }
    /**
     * Cobrancas da escola para o risco de atraso: as nao canceladas com
     * vencimento a partir de {@code desde} e as em aberto de qualquer data. So
     * as colunas das regras, com a data do pagamento online quando ha.
     */
    @Query(SELECT_PARA_RISCO + "WHERE c.escolaId = :escolaId " + FILTRO_PARA_RISCO)
    List<CobrancaParaRisco> buscarParaRisco(@Param("escolaId") UUID escolaId, @Param("desde") LocalDate desde);

    /** {@link #buscarParaRisco} de um aluno. */
    @Query(SELECT_PARA_RISCO + "WHERE c.escolaId = :escolaId AND c.aluno.id = :alunoId " + FILTRO_PARA_RISCO)
    List<CobrancaParaRisco> buscarParaRiscoDoAluno(@Param("escolaId") UUID escolaId,
            @Param("alunoId") UUID alunoId, @Param("desde") LocalDate desde);

    String SELECT_PARA_RISCO = "SELECT c.aluno.id AS alunoId, c.valor AS valor, "
            + "c.dataVencimento AS dataVencimento, c.status AS status, c.pagaEm AS pagaEm, "
            + "p.dataPagamento AS dataPagamento FROM Cobranca c "
            + "LEFT JOIN PagamentoCobranca p ON p.cobrancaId = c.id AND p.escolaId = c.escolaId ";

    String FILTRO_PARA_RISCO = "AND c.status <> br.pucminas.eduka.backend.domain.StatusCobranca.CANCELADA "
            + "AND (c.dataVencimento >= :desde OR c.status IN (br.pucminas.eduka.backend.domain.StatusCobranca.PENDENTE, "
            + "br.pucminas.eduka.backend.domain.StatusCobranca.EM_ATRASO))";

    /**
     * Cobranca vista pelo risco de atraso.
     *
     * {@code dataPagamento} vem do pagamento online; na baixa manual e nula e
     * vale {@code pagaEm}.
     */
    interface CobrancaParaRisco {
        UUID getAlunoId();

        BigDecimal getValor();

        LocalDate getDataVencimento();

        StatusCobranca getStatus();

        Instant getPagaEm();

        LocalDate getDataPagamento();
    }
}
