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
@Table(name = "ESCOLA")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Escola {

    @Id
    @Column(name = "ID_ESCOLA")
    private UUID id;

    @Column(name = "NOME", nullable = false)
    private String nome;

    @Column(name = "CNPJ_CPF")
    private String cnpjCpf;

    @Column(name = "ENDERECO")
    private String endereco;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "TELEFONE")
    private String telefone;

    @Column(name = "ATIVO", nullable = false)
    private boolean ativo;

    @Column(name = "CRIADO_EM", nullable = false)
    private Instant criadoEm;
}
