package br.pucminas.eduka.backend.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import br.pucminas.eduka.backend.domain.Notificacao;
import br.pucminas.eduka.backend.domain.StatusNotificacao;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    /**
     * Ids das pendentes que ja podem ser tentadas, as mais atrasadas primeiro.
     * So os ids: cada uma e travada e enviada na propria transacao.
     */
    @Query("SELECT n.id FROM Notificacao n WHERE n.status = :status AND n.proximaTentativaEm <= :agora "
            + "ORDER BY n.proximaTentativaEm")
    List<UUID> buscarIdsProntas(@Param("status") StatusNotificacao status, @Param("agora") Instant agora,
            Pageable lote);

    /**
     * Trava a notificacao para envio. {@code SKIP LOCKED} (timeout -2 no
     * Hibernate): se outra execucao ja esta com ela, volta vazio em vez de
     * esperar, e ninguem envia em dobro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT n FROM Notificacao n WHERE n.id = :id")
    Optional<Notificacao> travarParaEnvio(@Param("id") UUID id);

    /**
     * Apaga os e-mails ligados a esses registros (o login e as cobrancas de um
     * aluno anonimizado): a copia guardada traz nome e e-mail dele.
     */
    @Modifying
    @Query("DELETE FROM Notificacao n WHERE n.referenciaId IN :referencias")
    int apagarDasReferencias(@Param("referencias") Collection<UUID> referencias);
}
