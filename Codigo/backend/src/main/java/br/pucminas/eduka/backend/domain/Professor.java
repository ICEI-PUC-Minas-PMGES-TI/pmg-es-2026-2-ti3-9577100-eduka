package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.time.LocalDate;
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
 * Professor de uma escola. Mapeia a tabela {@code PROFESSOR} (V11). Dado
 * pessoal (nome, cpf, e-mail, telefone) fica em {@link Pessoa}, referenciada
 * por {@code pessoaId} - aqui so o que e do contexto academico.
 *
 * <p>
 * {@code escolaId} e o tenant: nunca vem do cliente, e sempre copiado do
 * token do usuario autenticado (ver
 * {@link br.pucminas.eduka.backend.service.ProfessorService}).
 * </p>
 */
@Entity
@Table(name = "PROFESSOR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Professor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_PROFESSOR", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "PESSOA_ID", nullable = false, updatable = false)
    private UUID pessoaId;

    /** Login do professor (V12). Nulo enquanto o acesso nao foi criado. */
    @Column(name = "USUARIO_ID")
    private UUID usuarioId;

    @Column(name = "FORMACAO", length = 150)
    private String formacao;

    @Column(name = "DATA_ADMISSAO")
    private LocalDate dataAdmissao;

    /** Regime e carga horaria (V13). Nulo nos professores anteriores a ela. */
    @Enumerated(EnumType.STRING)
    @Column(name = "VINCULO")
    private VinculoProfessor vinculo;

    @Builder.Default
    @Column(name = "ATIVO", nullable = false)
    private boolean ativo = true;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;
}
