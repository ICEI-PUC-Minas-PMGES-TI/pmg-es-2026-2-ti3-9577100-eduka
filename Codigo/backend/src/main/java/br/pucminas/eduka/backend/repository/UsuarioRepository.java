package br.pucminas.eduka.backend.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByLogin(String login);

    boolean existsByLogin(String login);

    List<Usuario> findAllByEscolaIdAndPerfil(UUID escolaId, PerfilUsuario perfil);

    Optional<Usuario> findByIdAndEscolaIdAndPerfil(UUID id, UUID escolaId, PerfilUsuario perfil);

    List<Usuario> findAllByEscolaIdAndPerfilIn(UUID escolaId, Collection<PerfilUsuario> perfis);

    Optional<Usuario> findByIdAndEscolaIdAndPerfilIn(UUID id, UUID escolaId, Collection<PerfilUsuario> perfis);

    Optional<Usuario> findByPessoaId(UUID pessoaId);

    /**
     * Grava o login concluido em {@code ULTIMO_ACESSO_EM} (V42). Nativo e fora
     * da entidade de proposito: um {@code save} de um {@link Usuario} lido
     * antes nao volta a coluna para o valor antigo.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE USUARIO SET ULTIMO_ACESSO_EM = :agora WHERE ID_USUARIO = :id", nativeQuery = true)
    int registrarAcesso(@Param("id") UUID id, @Param("agora") Instant agora);
}
