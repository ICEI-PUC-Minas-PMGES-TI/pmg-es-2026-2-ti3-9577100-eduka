package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
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
 * Aluno matriculado numa escola. Mapeia a tabela {@code alunos} (V5).
 *
 * <p>
 * {@code escolaId} e o tenant: nunca vem do cliente, e sempre copiado do
 * token do usuario autenticado no momento do cadastro e usado como filtro em
 * toda consulta (ver {@link br.pucminas.eduka.backend.service.AlunoService}).
 * </p>
 */
@Entity
@Table(name = "alunos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_aluno", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "escola_id", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "nome_aluno", nullable = false, length = 150)
    private String nome;

    @Column(name = "numero_matricula", nullable = false, length = 50)
    private String matricula;

    @Column(name = "email_responsavel", length = 150)
    private String emailResponsavel;

    @Column(name = "telefone_responsavel", length = 20)
    private String telefoneResponsavel;

    @Column(name = "genero_aluno", length = 30)
    private String genero;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;
}
