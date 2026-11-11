package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aceite de uma versao do aviso de privacidade por um usuario (IU29).
 * Mapeia {@code ACEITE_PRIVACIDADE} (V46). Sem {@code @TenantId}: o
 * SUPER_ADMIN, que nao tem escola, tambem aceita. Nunca muda depois de gravado.
 */
@Entity
@Table(name = "ACEITE_PRIVACIDADE")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AceitePrivacidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_ACEITE_PRIVACIDADE", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "USUARIO_ID", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "VERSAO", nullable = false, length = 20, updatable = false)
    private String versao;

    @Column(name = "ACEITO_EM", nullable = false, updatable = false)
    private Instant aceitoEm;
}
