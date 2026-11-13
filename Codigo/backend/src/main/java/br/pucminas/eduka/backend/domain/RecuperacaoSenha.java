package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

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
 * Pedido de recuperacao de senha em andamento: o codigo enviado por e-mail e
 * os contadores que limitam quantos codigos saem e quantos palpites valem (ver
 * {@link br.pucminas.eduka.backend.service.RecuperacaoSenhaService}).
 */
@Entity
@Table(name = "RECUPERACAO_SENHA")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_RECUPERACAO")
    private UUID id;

    @Column(name = "USUARIO_ID", nullable = false, unique = true)
    private UUID usuarioId;

    /** Hash do codigo, com o mesmo encoder das senhas. Nunca o codigo em si. */
    @Column(name = "CODIGO_HASH", nullable = false)
    private String codigoHash;

    @Column(name = "EXPIRA_EM", nullable = false)
    private Instant expiraEm;

    /** Codigos errados digitados na janela, somando todos os envios. */
    @Column(name = "TENTATIVAS", nullable = false)
    private int tentativas;

    /** Codigos enviados na janela, contando o primeiro. */
    @Column(name = "ENVIOS", nullable = false)
    private int envios;

    @Column(name = "ULTIMO_ENVIO_EM", nullable = false)
    private Instant ultimoEnvioEm;

    /** Inicio da janela em que valem os limites de envios e de tentativas. */
    @Column(name = "JANELA_INICIO", nullable = false)
    private Instant janelaInicio;

    public boolean expirada(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    /** Evita que o hash do codigo vaze em log. */
    @Override
    public String toString() {
        return "RecuperacaoSenha[id=%s, usuarioId=%s]".formatted(id, usuarioId);
    }
}
