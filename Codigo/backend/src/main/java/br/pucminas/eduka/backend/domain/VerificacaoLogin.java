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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Segunda etapa de um login em andamento: a senha ja conferiu e falta o
 * codigo enviado por e-mail (ver
 * {@link br.pucminas.eduka.backend.service.VerificacaoLoginService}).
 */
@Entity
@Table(name = "VERIFICACAO_LOGIN")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VerificacaoLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_VERIFICACAO")
    private UUID id;

    @Column(name = "USUARIO_ID", nullable = false)
    private UUID usuarioId;

    /** Hash do codigo, com o mesmo encoder das senhas. Nunca o codigo em si. */
    @Column(name = "CODIGO_HASH", nullable = false)
    private String codigoHash;

    @Column(name = "EXPIRA_EM", nullable = false)
    private Instant expiraEm;

    /** Codigos errados digitados desde o ultimo envio. */
    @Column(name = "TENTATIVAS", nullable = false)
    private int tentativas;

    /** Quantos codigos ja foram enviados nesta verificacao, contando o primeiro. */
    @Column(name = "ENVIOS", nullable = false)
    private int envios;

    @Column(name = "ULTIMO_ENVIO_EM", nullable = false)
    private Instant ultimoEnvioEm;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    public boolean expirada(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    /** Evita que o hash do codigo vaze em log. */
    @Override
    public String toString() {
        return "VerificacaoLogin[id=%s, usuarioId=%s]".formatted(id, usuarioId);
    }
}
