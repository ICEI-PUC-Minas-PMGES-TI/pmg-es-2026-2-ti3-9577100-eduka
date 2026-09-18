package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "USUARIO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Usuario {

    @Id
    @Column(name = "ID_USUARIO")
    private UUID id;

    @Column(name = "EMPRESA_ID", nullable = false)
    private UUID empresaId;

    @Column(name = "PESSOA_ID", nullable = false)
    private UUID pessoaId;

    @Column(name = "LOGIN", nullable = false, unique = true)
    private String login;

    @Column(name = "SENHA_HASH", nullable = false)
    private String senhaHash;

    @Column(name = "PERFIL", nullable = false)
    private String perfil;

    @Column(name = "ATIVO", nullable = false)
    private boolean ativo;

    @Column(name = "CRIADO_EM", nullable = false)
    private Instant criadoEm;
}
