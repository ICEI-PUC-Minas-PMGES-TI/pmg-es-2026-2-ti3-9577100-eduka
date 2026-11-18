package br.pucminas.eduka.backend.domain.financeiro;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pagador ja cadastrado como cliente numa conta do provedor. Mapeia a tabela
 * {@code CLIENTE_PROVEDOR} (V26): um por pessoa na conta filha da escola e um
 * por escola na conta-pai. Evita cadastrar o mesmo pagador de novo.
 *
 * <p>
 * {@code escolaId} e o tenant: sempre o de quem paga.
 * </p>
 */
@Entity
@Table(name = "CLIENTE_PROVEDOR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteProvedor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_CLIENTE_PROVEDOR", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "CONTA", nullable = false, length = 5, updatable = false)
    private ContaProvedorCliente conta;

    /** {@code EDK-PES-<id da pessoa>} ou {@code EDK-ESC-<id da escola>}. */
    @Column(name = "REFERENCIA_EXTERNA", nullable = false, length = 60, updatable = false)
    private String referenciaExterna;

    @Column(name = "ID_CLIENTE_EXTERNO", nullable = false, length = 64)
    private String idClienteExterno;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;
}
