package br.pucminas.eduka.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pii.encryption")
public record PiiEncryptionProperties(String key, int keyVersion) {
}
