package br.pucminas.eduka.backend.model;

import java.time.LocalDateTime;
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
 * Credencial de acesso ao sistema, mapeando a tabela {@code USUARIO} criada na
 * migration V1.
 *
 * <p>
 * Nem toda {@code PESSOA} tem um {@code USUARIO}: apenas quem efetivamente faz
 * login. Os seis perfis do sistema compartilham esta mesma tabela, distinguidos
 * pela coluna {@code PERFIL}.
 * </p>
 *
 * <p>
 * {@code empresaId} e {@code pessoaId} estao mapeados como UUID simples, e nao
 * como {@code @ManyToOne}, porque as entidades Empresa e Pessoa ainda nao
 * existem no codigo. As chaves estrangeiras continuam sendo garantidas pelo
 * banco.
 * </p>
 */
@Entity
@Table(name = "USUARIO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_USUARIO", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "EMPRESA_ID", nullable = false)
    private UUID empresaId;

    @Column(name = "PESSOA_ID", nullable = false)
    private UUID pessoaId;

    @Column(name = "LOGIN", nullable = false, unique = true, length = 150)
    private String login;

    /**
     * Hash da senha com prefixo do algoritmo. Nunca recebe a senha em texto puro
     * — ver {@link br.pucminas.eduka.backend.service.UsuarioService}.
     */
    @Column(name = "SENHA_HASH", nullable = false, columnDefinition = "text")
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "PERFIL", nullable = false, columnDefinition = "text")
    private PerfilUsuario perfil;

    @Builder.Default
    @Column(name = "ATIVO", nullable = false)
    private boolean ativo = true;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    /**
     * Evita que a senha vaze em log, stack trace ou resposta de erro.
     */
    @Override
    public String toString() {
        return "Usuario[id=%s, login=%s, perfil=%s, ativo=%s]".formatted(id, login, perfil, ativo);
    }
}
