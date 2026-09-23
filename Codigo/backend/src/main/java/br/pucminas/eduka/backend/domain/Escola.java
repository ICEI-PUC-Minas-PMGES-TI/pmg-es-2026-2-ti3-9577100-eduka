package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    @Embedded
    private Endereco endereco;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "TELEFONE")
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "PLANO")
    private PlanoEscola plano;

    @Column(name = "ATIVO", nullable = false)
    private boolean ativo;

    @Column(name = "INICIO_VIGENCIA")
    private LocalDate inicioVigencia;

    @Column(name = "FIM_VIGENCIA")
    private LocalDate fimVigencia;

    @Column(name = "LIMITE_USUARIOS")
    private Integer limiteUsuarios;

    /** SUBSELECT: a listagem carrega os modulos de todas as escolas numa query so. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "ESCOLA_MODULO", joinColumns = @JoinColumn(name = "ESCOLA_ID"))
    @Enumerated(EnumType.STRING)
    @Column(name = "MODULO", nullable = false)
    private Set<ModuloEscola> modulos = new HashSet<>();

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
    /** A vigencia comeca na data do cadastro e esse inicio nao muda depois. */
    public static Escola cadastrar(String nome, String cnpjCpf, Endereco endereco, String email, String telefone,
            PlanoEscola plano, boolean ativo) {
        return new Escola(null, nome, cnpjCpf, endereco, email, telefone, plano, ativo, LocalDate.now(), null, null,
                new HashSet<>(), null, 0L);
    }

    /**
     * Atualiza os dados cadastrais. Campo nulo mantem o valor atual; texto em
     * branco limpa o campo. O endereco, quando informado, e trocado inteiro.
     */
    public void atualizarDados(String nome, String cnpjCpf, Endereco endereco, String email, String telefone,
            PlanoEscola plano) {
        this.nome = nome.trim();
        this.cnpjCpf = manterOuLimpar(this.cnpjCpf, cnpjCpf);
        if (endereco != null) {
            this.endereco = Endereco.normalizado(endereco.logradouro(), endereco.numero(), endereco.bairro(),
                    endereco.cidade(), endereco.estado(), endereco.cep());
        }
        this.email = manterOuLimpar(this.email, email);
        this.telefone = manterOuLimpar(this.telefone, telefone);
        if (plano != null) {
            this.plano = plano;
        }
    }

    /**
     * Atualiza termino da vigencia, limite de usuarios e modulos habilitados.
     * Campo nulo mantem o valor atual; a lista de modulos, quando informada,
     * substitui a anterior. O inicio da vigencia nao e alteravel.
     */
    public void atualizarAssinatura(LocalDate fimVigencia, Integer limiteUsuarios,
            Collection<ModuloEscola> modulos) {
        LocalDate novoFim = fimVigencia != null ? fimVigencia : this.fimVigencia;
        if (this.inicioVigencia != null && novoFim != null && novoFim.isBefore(this.inicioVigencia)) {
            throw new IllegalArgumentException("Termino da vigencia deve ser igual ou posterior ao inicio");
        }
        if (limiteUsuarios != null && limiteUsuarios <= 0) {
            throw new IllegalArgumentException("Limite de usuarios deve ser maior que zero");
        }
        this.fimVigencia = novoFim;
        if (limiteUsuarios != null) {
            this.limiteUsuarios = limiteUsuarios;
        }
        if (modulos != null) {
            this.modulos.clear();
            this.modulos.addAll(modulos.isEmpty() ? Set.of() : EnumSet.copyOf(modulos));
        }
    }

    public void ativar() {
        this.ativo = true;
    }

    public void suspender() {
        this.ativo = false;
    }

    private static String manterOuLimpar(String atual, String novo) {
        if (novo == null) {
            return atual;
        }
        return novo.isBlank() ? null : novo.trim();
    }
}
