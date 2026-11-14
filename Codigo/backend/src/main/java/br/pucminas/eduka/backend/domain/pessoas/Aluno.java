package br.pucminas.eduka.backend.domain.pessoas;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.TenantId;

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
 * Aluno matriculado numa escola. Mapeia a tabela {@code alunos} (V5, V10).
 * Dado pessoal (nome, cpf, data de nascimento, endereco) fica em
 * {@link Pessoa}, referenciada por {@code pessoaId} - aqui so o que e
 * intrinseco ao contexto academico (matricula, genero).
 *
 * <p>
 * {@code escolaId} e o tenant: nunca vem do cliente, e sempre copiado do
 * token do usuario autenticado no momento do cadastro e usado como filtro em
 * toda consulta (ver {@link br.pucminas.eduka.backend.service.pessoas.AlunoService}).
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

    @TenantId
    @Column(name = "escola_id", nullable = false, updatable = false)
    private UUID escolaId;

    @Column(name = "pessoa_id", nullable = false, updatable = false)
    private UUID pessoaId;

    /** Login do aluno (V16). Nulo enquanto o acesso nao foi criado. */
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "numero_matricula", nullable = false, length = 50)
    private String matricula;

    @Column(name = "genero_aluno", length = 30)
    private String genero;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    /**
     * Quando o dado pessoal foi apagado (LGPD, V45). A linha fica, porque
     * cobrancas, matriculas e notas apontam para ela.
     */
    @Column(name = "anonimizado_em")
    private Instant anonimizadoEm;

    public boolean isAnonimizado() {
        return anonimizadoEm != null;
    }
}
