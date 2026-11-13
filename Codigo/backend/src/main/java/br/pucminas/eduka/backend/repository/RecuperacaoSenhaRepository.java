package br.pucminas.eduka.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.pucminas.eduka.backend.domain.RecuperacaoSenha;
import jakarta.persistence.LockModeType;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, UUID> {

    /**
     * Le travando a linha: sem isso, palpites disparados em paralelo leriam o
     * mesmo contador de tentativas e passariam do limite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecuperacaoSenha r where r.usuarioId = :usuarioId")
    Optional<RecuperacaoSenha> findParaAtualizarByUsuarioId(@Param("usuarioId") UUID usuarioId);
}
