package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Cadastro sigiloso compartilhado entre todos os papeis (aluno, responsavel,
 * professor, funcionario, usuario). Nome, CPF, data de nascimento, e-mail,
 * telefone e endereco ficam cifrados com AES-256-GCM antes de chegar ao
 * Postgres (ver PessoaCryptoConverter).
 *
 * <p>
 * Diferente de {@link Aluno}, esta entidade nao usa
 * {@link org.hibernate.annotations.TenantId @TenantId}: o
 * {@code SUPER_ADMIN} nao pertence a nenhuma escola e ainda assim cria
 * pessoas/usuarios em nome de escolas alheias (ver
 * {@code HierarquiaPerfis}), o que o filtro automatico de tenant do
 * Hibernate nao consegue expressar (ele sempre usa a escola de quem esta
 * fazendo a chamada, nunca uma escola-alvo arbitraria). Por isso toda leitura
 * de {@code Pessoa} tem que passar pelos metodos escopados por escola de
 * {@link br.pucminas.eduka.backend.repository.PessoaRepository} (nunca
 * {@code findById}/{@code findAllById} puros) - nao ha rede de seguranca do
 * Hibernate aqui.
 * </p>
 */
@Entity
@Table(name = "PESSOA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pessoa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_PESSOA")
    private UUID id;

    @Column(name = "ESCOLA_ID")
    private UUID escolaId;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "NOME", nullable = false)
    private String nome;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "CPF")
    private String cpf;

    /** ISO-8601 (yyyy-MM-dd); conversao para/de LocalDate fica na service/DTO. */
    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "DATA_NASCIMENTO")
    private String dataNascimento;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "EMAIL")
    private String email;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "TELEFONE")
    private String telefone;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "LOGRADOURO")
    private String logradouro;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "NUMERO")
    private String numero;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "BAIRRO")
    private String bairro;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "CIDADE")
    private String cidade;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "ESTADO")
    private String estado;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "CEP")
    private String cep;

    /** Preenchido pelo DEFAULT da coluna; suporte a rotacao futura de chave. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CHAVE_VERSAO", nullable = false, updatable = false)
    private short chaveVersao;

    @Builder.Default
    @Column(name = "ATIVO", nullable = false)
    private boolean ativo = true;

    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;
}
