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
 * Responsavel (legal e/ou financeiro) de alunos da escola. Mapeia a tabela
 * {@code RESPONSAVEL} (V23). Dado pessoal (nome, cpf, e-mail, telefone,
 * endereco) fica em {@link Pessoa}, referenciada por {@code pessoaId}.
 *
 * <p>
 * O vinculo com cada aluno (parentesco, se e o financeiro) fica em
 * {@link AlunoResponsavel}, que liga pelo {@code pessoaId}: um mesmo
 * responsavel pode ter varios alunos (irmaos).
 * </p>
 *
 * <p>
 * {@code escolaId} e o tenant: nunca vem do cliente, e sempre copiado do
 * token do usuario autenticado (ver
 * {@link br.pucminas.eduka.backend.service.ResponsavelService}).
 * </p>
 */
@Entity
@Table(name = "RESPONSAVEL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Responsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_RESPONSAVEL", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "ESCOLA_ID", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "PESSOA_ID", nullable = false, updatable = false)
    private UUID pessoaId;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;
}
