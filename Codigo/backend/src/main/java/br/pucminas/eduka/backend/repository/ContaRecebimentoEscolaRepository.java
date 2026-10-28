package br.pucminas.eduka.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;

@Repository
public interface ContaRecebimentoEscolaRepository extends JpaRepository<ContaRecebimentoEscola, UUID> {

    /** Conta da escola; vazio enquanto o MASTER nao ativou os recebimentos. */
    Optional<ContaRecebimentoEscola> findByEscolaId(UUID escolaId);

    /**
     * Escola dona da conta no provedor. Nativo: o webhook chega sem usuario
     * autenticado, e o {@code @TenantId} filtraria por uma escola que nao existe.
     */
    @Query(value = "SELECT ESCOLA_ID FROM CONTA_RECEBIMENTO_ESCOLA WHERE ID_CONTA_EXTERNA = :idContaExterna",
            nativeQuery = true)
    Optional<UUID> buscarEscolaPorIdContaExterna(@Param("idContaExterna") String idContaExterna);

    /** Conta e escola pelo id da conta no provedor, com o status atual. Nativo pelo mesmo motivo. */
    @Query(value = "SELECT ID_CONTA_RECEBIMENTO AS id, ESCOLA_ID AS escolaId, STATUS AS status "
            + "FROM CONTA_RECEBIMENTO_ESCOLA WHERE ID_CONTA_EXTERNA = :idContaExterna", nativeQuery = true)
    Optional<ContaPorIdExterno> buscarPorIdContaExterna(@Param("idContaExterna") String idContaExterna);

    /** Contas criadas no provedor que ainda esperam a aprovacao. Nativo: a rotina roda sem usuario. */
    @Query(value = "SELECT ID_CONTA_EXTERNA AS idContaExterna, CREDENCIAL_CIFRADA AS credencialCifrada "
            + "FROM CONTA_RECEBIMENTO_ESCOLA WHERE STATUS = 'AGUARDANDO_APROVACAO' "
            + "AND CREDENCIAL_CIFRADA IS NOT NULL ORDER BY SINCRONIZADA_EM NULLS FIRST", nativeQuery = true)
    List<ContaParaSincronizar> listarAguardandoAprovacao();

    /**
     * Grava a situacao lida do provedor. So mexe em conta ja criada (com
     * credencial): uma conta em criacao nao muda por aviso. A data de aprovacao
     * fica a da primeira vez.
     *
     * @return 1 se a conta foi atualizada, 0 se nao existe ou ainda nao foi criada
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE CONTA_RECEBIMENTO_ESCOLA SET STATUS = :status, SITUACAO_GERAL = :geral, "
            + "SITUACAO_DOCUMENTACAO = :documentacao, SITUACAO_DADOS_COMERCIAIS = :dadosComerciais, "
            + "SITUACAO_CONTA_BANCARIA = :contaBancaria, "
            + "APROVADA_EM = CASE WHEN :status = 'APROVADA' THEN COALESCE(APROVADA_EM, :agora) ELSE APROVADA_EM END, "
            + "SINCRONIZADA_EM = :agora, ATUALIZADO_EM = :agora, VERSAO = VERSAO + 1 "
            + "WHERE ID_CONTA_EXTERNA = :idContaExterna "
            + "AND STATUS IN ('AGUARDANDO_APROVACAO', 'APROVADA', 'REJEITADA')", nativeQuery = true)
    int gravarSituacao(@Param("idContaExterna") String idContaExterna, @Param("status") String status,
            @Param("geral") String geral, @Param("documentacao") String documentacao,
            @Param("dadosComerciais") String dadosComerciais, @Param("contaBancaria") String contaBancaria,
            @Param("agora") Instant agora);

    /**
     * CRIANDO antigo: a aplicacao caiu no meio da criacao. Vira
     * VERIFICACAO_PENDENTE para a proxima ativacao procurar a conta pelo CNPJ.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE CONTA_RECEBIMENTO_ESCOLA SET STATUS = 'VERIFICACAO_PENDENTE', ULTIMO_ERRO = :motivo, "
            + "ATUALIZADO_EM = :agora, VERSAO = VERSAO + 1 "
            + "WHERE STATUS = 'CRIANDO' AND SOLICITADA_EM < :limite", nativeQuery = true)
    int liberarCriacaoSemResposta(@Param("limite") Instant limite, @Param("motivo") String motivo,
            @Param("agora") Instant agora);

    interface ContaPorIdExterno {
        UUID getId();

        UUID getEscolaId();

        String getStatus();
    }

    interface ContaParaSincronizar {
        String getIdContaExterna();

        byte[] getCredencialCifrada();
    }
}
