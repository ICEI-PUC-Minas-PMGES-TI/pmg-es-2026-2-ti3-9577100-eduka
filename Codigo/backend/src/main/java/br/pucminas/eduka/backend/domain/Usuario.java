package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "USUARIO")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_USUARIO")
    private UUID id;

    @Column(name = "ESCOLA_ID")
    private UUID escolaId;

    @Column(name = "PESSOA_ID", nullable = false)
    private UUID pessoaId;

    @Column(name = "LOGIN", nullable = false, unique = true)
    private String login;

    /**
     * Hash da senha com prefixo do algoritmo. Nunca recebe a senha em texto
     * puro — ver {@link br.pucminas.eduka.backend.service.UsuarioService}.
     */
    @Column(name = "SENHA_HASH", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "PERFIL", nullable = false)
    private PerfilUsuario perfil;

    @Column(name = "ATIVO", nullable = false)
    private boolean ativo;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    /**
     * Evita que a senha vaze em log, stack trace ou resposta de erro.
     */
    @Override
    public String toString() {
        return "Usuario[id=%s, login=%s, perfil=%s, ativo=%s]".formatted(id, login, perfil, ativo);
    }
}
