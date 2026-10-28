package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

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
import lombok.Setter;

/**
 * Categoria em que a escola lanca as despesas. Mapeia a tabela
 * {@code CATEGORIA_DESPESA} (V31); cada escola tem as suas.
 */
@Entity
@Table(name = "CATEGORIA_DESPESA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaDespesa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_CATEGORIA_DESPESA", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "NOME", nullable = false, length = 80)
    private String nome;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;
}
