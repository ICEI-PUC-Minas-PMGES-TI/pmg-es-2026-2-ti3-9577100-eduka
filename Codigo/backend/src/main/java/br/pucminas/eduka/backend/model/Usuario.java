package br.pucminas.eduka.backend.model;

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
import lombok.Setter;

/**
 * Credencial de acesso ao sistema, mapeando a tabela {@code usuarios}
 * (criada na V1 como USUARIO e renomeada na V3).
 *
 * <p>
 * Nem toda pessoa tem um usuario: apenas quem efetivamente faz login. Os seis
 * perfis do sistema compartilham esta mesma tabela, distinguidos pela coluna
 * {@code perfil}.
 * </p>
 *
 * <p>
 * {@code escolaId} e {@code pessoaId} estao mapeados como UUID simples, e nao
 * como {@code @ManyToOne}, para nao acoplar esta entidade a Escola e Pessoa
 * antes da hora. O banco garante as chaves estrangeiras, inclusive que a
 * pessoa pertence a mesma escola do usuario (FK composta na V3).
 * </p>
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_usuario", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "escola_id", nullable = false)
    private UUID escolaId;

    @Column(name = "pessoa_id", nullable = false)
    private UUID pessoaId;

    @Column(name = "login", nullable = false, unique = true, length = 150)
    private String login;

    /**
     * Hash da senha com prefixo do algoritmo. Nunca recebe a senha em texto puro
     * — ver {@link br.pucminas.eduka.backend.service.UsuarioService}.
     */
    @Column(name = "senha_hash", nullable = false, columnDefinition = "text")
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, columnDefinition = "text")
    private PerfilUsuario perfil;

    @Builder.Default
    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    /**
     * Evita que a senha vaze em log, stack trace ou resposta de erro.
     */
    @Override
    public String toString() {
        return "Usuario[id=%s, login=%s, perfil=%s, ativo=%s]".formatted(id, login, perfil, ativo);
    }
}
