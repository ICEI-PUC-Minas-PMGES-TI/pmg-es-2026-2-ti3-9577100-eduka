package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
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

@Entity
@Table(name = "PESSOA_PAPEL")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PessoaPapel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_PESSOA_PAPEL")
    private UUID id;

    @Column(name = "PESSOA_ID", nullable = false)
    private UUID pessoaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PAPEL", nullable = false)
    private PapelPessoa papel;

    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;
}
