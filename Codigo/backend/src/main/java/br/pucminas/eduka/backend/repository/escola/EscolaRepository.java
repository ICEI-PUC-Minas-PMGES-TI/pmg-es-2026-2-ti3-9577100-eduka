package br.pucminas.eduka.backend.repository.escola;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.escola.MotivoSuspensao;

public interface EscolaRepository extends JpaRepository<Escola, UUID> {

    boolean existsByCnpjCpf(String cnpjCpf);

    boolean existsByCnpjCpfAndIdNot(String cnpjCpf, UUID id);

    /**
     * Ativa e motivo da suspensao, sem carregar a escola (e os modulos): roda a
     * cada requisicao autenticada ({@code EscolaSuspensaFilter}).
     */
    @Query("SELECT e.ativo AS ativa, e.motivoSuspensao AS motivo FROM Escola e WHERE e.id = :id")
    Optional<Situacao> situacao(@Param("id") UUID id);

    /**
     * Reativar a escola recomeca o ciclo da suspensao por atraso (V43): as
     * faturas em aberto voltam a receber o aviso antes de suspender de novo.
     */
    @Modifying
    @Query(value = "UPDATE FATURA_PLATAFORMA SET AVISO_SUSPENSAO_EM = NULL, SUSPENSAO_PREVISTA_PARA = NULL "
            + "WHERE ESCOLA_ID = :escolaId AND STATUS IN ('PENDENTE', 'EM_ATRASO')", nativeQuery = true)
    int reiniciarAvisosDeSuspensao(@Param("escolaId") UUID escolaId);

    /**
     * Modulos contratados, sem carregar a escola: roda nas requisicoes que
     * dependem de modulo ({@code ModuloContratadoFilter}) e no login.
     */
    @Query(value = "SELECT MODULO FROM ESCOLA_MODULO WHERE ESCOLA_ID = :id", nativeQuery = true)
    List<String> modulos(@Param("id") UUID id);

    /**
     * Limite de usuarios da escola, travando a linha dela ate o fim da
     * transacao: dois cadastros ao mesmo tempo nao passam juntos do limite
     * (ver {@code LimiteUsuariosService}). Vazio sem limite ou sem escola.
     * Exige a transacao de quem chama: sozinho, o repositorio abriria uma
     * so de leitura, onde o Postgres recusa o {@code FOR UPDATE}, e a trava
     * acabaria junto com ela.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT LIMITE_USUARIOS FROM ESCOLA WHERE ID_ESCOLA = :id FOR UPDATE", nativeQuery = true)
    Optional<Integer> travarLimiteUsuarios(@Param("id") UUID id);

    interface Situacao {
        boolean isAtiva();

        MotivoSuspensao getMotivo();
    }
}
