package br.pucminas.eduka.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * E-mail na fila de envio (tabela {@code NOTIFICACAO}, V25). Quem cria e
 * {@link br.pucminas.eduka.backend.email.NotificacaoService}; quem envia e
 * {@link br.pucminas.eduka.backend.email.NotificacaoProcessador}.
 *
 * <p>
 * Destinatario, assunto e corpo sao cifrados como em {@link Pessoa}. Sem
 * {@link org.hibernate.annotations.TenantId @TenantId}: a fila e da
 * plataforma, processada por rotina sem usuario autenticado, e nunca e
 * exposta por endpoint de escola.
 * </p>
 */
@Entity
@Table(name = "NOTIFICACAO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_NOTIFICACAO", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, updatable = false, length = 40)
    private TipoNotificacao tipo;

    @Column(name = "REFERENCIA_ID", updatable = false)
    private UUID referenciaId;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "DESTINATARIO", nullable = false, updatable = false)
    private String destinatario;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "NOME_DESTINATARIO", updatable = false)
    private String nomeDestinatario;

    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "ASSUNTO", nullable = false, updatable = false)
    private String assunto;

    /** Apagado quando a notificacao chega a um estado final. */
    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "HTML")
    private String html;

    /** Apagado quando a notificacao chega a um estado final. */
    @Convert(converter = PessoaCryptoConverter.class)
    @Column(name = "TEXTO")
    private String texto;

    /** Preenchido pelo DEFAULT da coluna; suporte a rotacao futura de chave. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CHAVE_VERSAO", nullable = false, updatable = false)
    private short chaveVersao;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatusNotificacao status;

    @Column(name = "TENTATIVAS", nullable = false)
    private int tentativas;

    @Column(name = "PROXIMA_TENTATIVA_EM", nullable = false)
    private Instant proximaTentativaEm;

    /** Nulo: nao expira. */
    @Column(name = "VALIDA_ATE", updatable = false)
    private Instant validaAte;

    /** Tipo do erro e status HTTP, nunca a mensagem do provedor (pode ecoar o destinatario). */
    @Column(name = "ULTIMO_ERRO", length = 255)
    private String ultimoErro;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ENVIADO_EM")
    private Instant enviadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;

    /** Leva a um estado final e descarta o corpo, que so servia para o envio. */
    public void finalizar(StatusNotificacao novo, Instant agora) {
        status = novo;
        html = null;
        texto = null;
        atualizadoEm = agora;
        if (novo == StatusNotificacao.ENVIADA) {
            enviadoEm = agora;
        }
    }
}
