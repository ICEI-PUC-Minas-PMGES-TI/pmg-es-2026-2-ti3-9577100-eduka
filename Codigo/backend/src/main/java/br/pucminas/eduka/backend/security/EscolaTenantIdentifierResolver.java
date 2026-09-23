package br.pucminas.eduka.backend.security;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Informa ao Hibernate qual e a escola (tenant) da requisicao atual, a
 * partir do usuario autenticado no {@code SecurityContext} — nunca de um
 * parametro de requisicao.
 *
 * <p>
 * Toda entidade cujo campo de escola esteja anotado com
 * {@link org.hibernate.annotations.TenantId @TenantId} passa a ter
 * {@code escola_id = :tenantId} injetado automaticamente pelo Hibernate em
 * qualquer consulta (inclusive {@code findAll}, {@code findById} e queries
 * derivadas), e o campo e preenchido sozinho no insert. Isso torna o
 * isolamento por escola (RF05, RNF03) obrigatorio na camada de persistencia,
 * em vez de depender de cada service lembrar de filtrar manualmente.
 * </p>
 *
 * <p>
 * {@code SUPER_ADMIN} nao pertence a nenhuma escola e, por design
 * ({@link RotasPorPerfil}), nunca acessa entidades particionadas por tenant —
 * por isso {@link #resolveCurrentTenantIdentifier()} falha explicitamente
 * (em vez de devolver {@code null} e vazar/ocultar dados de forma silenciosa)
 * se for chamado sem uma escola no contexto.
 * </p>
 */
@Component
public class EscolaTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Nenhum usuario autenticado no contexto da requisicao");
        }
        UUID escolaId = usuario.escolaId();
        if (escolaId == null) {
            throw new IllegalStateException(
                    "Usuario sem escola (ex.: SUPER_ADMIN) nao pode acessar entidade particionada por escola");
        }
        return escolaId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
