package br.pucminas.eduka.backend.security;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga o {@link EscolaTenantIdentifierResolver} ao Hibernate, para que
 * entidades anotadas com {@code @TenantId} tenham o isolamento por escola
 * (RF05, RNF03) aplicado automaticamente em toda consulta.
 */
@Configuration
public class TenantConfig {

    @Bean
    public HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer(
            EscolaTenantIdentifierResolver resolver) {
        return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}