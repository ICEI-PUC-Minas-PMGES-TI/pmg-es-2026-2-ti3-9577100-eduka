package br.pucminas.eduka.backend.repository.acesso;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.pucminas.eduka.backend.domain.acesso.VerificacaoLogin;
import jakarta.persistence.LockModeType;

public interface VerificacaoLoginRepository extends JpaRepository<VerificacaoLogin, UUID> {

    /**
     * Le travando a linha: sem isso, palpites disparados em paralelo leriam o
     * mesmo contador de tentativas e passariam do limite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VerificacaoLogin v where v.id = :id")
    Optional<VerificacaoLogin> findParaAtualizarById(@Param("id") UUID id);

    @Modifying
    @Query("delete from VerificacaoLogin v where v.usuarioId = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") UUID usuarioId);
}
