package br.pucminas.eduka.backend.security;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
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
 * O Hibernate resolve o tenant ao abrir <em>qualquer</em> sessao — inclusive
 * na subida da aplicacao, no login (ainda sem usuario autenticado) e nas
 * requisicoes do {@code SUPER_ADMIN}, que nao pertence a nenhuma escola.
 * Nesses casos devolvemos {@link #SEM_ESCOLA}, um id que nao corresponde a
 * nenhuma escola: consultas a entidades particionadas voltam vazias e inserts
 * falham na FK. O isolamento continua fechado por padrao, sem impedir o uso
 * de entidades nao particionadas (escola, usuario).
 * </p>
 */
@Component
public class EscolaTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /** Tenant de quem nao tem escola no contexto; nao casa com nenhuma linha. */
    public static final UUID SEM_ESCOLA = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.escolaId() != null) {
            return usuario.escolaId();
        }
        return SEM_ESCOLA;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
