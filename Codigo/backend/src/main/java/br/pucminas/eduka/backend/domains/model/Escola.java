package br.pucminas.eduka.backend.domains.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import br.pucminas.eduka.backend.domains.enums.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Escola contratante: o tenant do sistema. Toda pessoa, usuario e dado de
 * negocio pertence a exatamente uma escola, via coluna {@code escola_id}.
 *
 * <p>
 * Mapeia a tabela {@code escolas} conforme a V3, que unificou a antiga
 * {@code EMPRESA} (V1) com a {@code escolas} original (V2) e trocou a chave
 * para UUID.
 * </p>
 */
@Entity
@Table(name = "escolas")
public class Escola {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_escola", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "cnpj", length = 20)
    private String cnpj;

    @Column(name = "endereco", length = 200)
    private String endereco;

    @Column(name = "email_responsavel", length = 150)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ATIVO;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

}
