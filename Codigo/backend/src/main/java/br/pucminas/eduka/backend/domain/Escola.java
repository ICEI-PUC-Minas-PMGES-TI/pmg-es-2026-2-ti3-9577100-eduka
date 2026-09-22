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
import jakarta.persistence.Version;

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
    @GeneratedValue(strategy = GenerationType.UUID)
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

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Version
    @Column(name = "VERSAO", nullable = false)
    private long versao;

    /**
     * Cadastro de uma escola nova (RF06). {@code id}, {@code criadoEm} e
     * {@code versao} sao geridos pelo banco/Hibernate.
     */
    public static Escola cadastrar(String nome, String cnpjCpf, String endereco, String email, String telefone,
            boolean ativo) {
        return new Escola(null, nome, cnpjCpf, endereco, email, telefone, ativo, null, 0L);
    }

    public void atualizarDados(String nome, String cnpjCpf, String endereco, String email, String telefone) {
        this.nome = nome;
        if (cnpjCpf != null) {
            this.cnpjCpf = cnpjCpf;
        }
        if (endereco != null) {
            this.endereco = endereco;
        }
        if (email != null) {
            this.email = email;
        }
        if (telefone != null) {
            this.telefone = telefone;
        }
    }

    public void ativar() {
        this.ativo = true;
    }

    public void suspender() {
        this.ativo = false;
    }
}
