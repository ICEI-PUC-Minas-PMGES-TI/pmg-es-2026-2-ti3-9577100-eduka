package br.pucminas.eduka.backend.repository.escola;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    interface Situacao {
        boolean isAtiva();

        MotivoSuspensao getMotivo();
    }
}
