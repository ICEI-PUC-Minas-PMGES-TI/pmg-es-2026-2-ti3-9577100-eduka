package br.pucminas.eduka.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.pucminas.eduka.backend.domain.Notificacao;
import br.pucminas.eduka.backend.domain.StatusNotificacao;
import br.pucminas.eduka.backend.domain.TipoNotificacao;
import br.pucminas.eduka.backend.repository.NotificacaoRepository;

/** Enfileirar grava na transacao de quem chamou e so tenta enviar depois do commit. */
@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-19T12:00:00Z");
    private static final Email EMAIL = new Email("ana@escola.com", "Ana", "Assunto", "<p>Oi</p>", "Oi");

    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private NotificacaoProcessador processador;

    private NotificacaoService service;

    @BeforeEach
    void setUp() {
        service = new NotificacaoService(notificacaoRepository, processador, Clock.fixed(AGORA, ZoneOffset.UTC));
        // Simula a transacao de negocio em andamento.
        TransactionSynchronizationManager.initSynchronization();
        when(notificacaoRepository.save(any(Notificacao.class))).thenAnswer(inv -> {
            Notificacao notificacao = inv.getArgument(0);
            notificacao.setId(UUID.randomUUID());
            return notificacao;
        });
    }

    @AfterEach
    void limpar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void commitar() {
        List<TransactionSynchronization> sincronizacoes = TransactionSynchronizationManager.getSynchronizations();
        sincronizacoes.forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void gravaPendenteProntaParaEnvioENaoEnviaAntesDoCommit() {
        UUID usuarioId = UUID.randomUUID();
        Instant validaAte = AGORA.plusSeconds(300);

        Notificacao notificacao = service.enfileirar(TipoNotificacao.CODIGO_LOGIN, usuarioId, EMAIL, validaAte);

        assertThat(notificacao.getStatus()).isEqualTo(StatusNotificacao.PENDENTE);
        assertThat(notificacao.getTipo()).isEqualTo(TipoNotificacao.CODIGO_LOGIN);
        assertThat(notificacao.getReferenciaId()).isEqualTo(usuarioId);
        assertThat(notificacao.getDestinatario()).isEqualTo("ana@escola.com");
        assertThat(notificacao.getHtml()).isEqualTo("<p>Oi</p>");
        assertThat(notificacao.getTentativas()).isZero();
        assertThat(notificacao.getProximaTentativaEm()).isEqualTo(AGORA);
        assertThat(notificacao.getValidaAte()).isEqualTo(validaAte);
        // Rollback antes do commit: nada sai.
        verifyNoInteractions(processador);
    }

    @Test
    void depoisDoCommitTentaEnviarNaHora() {
        Notificacao notificacao = service.enfileirar(TipoNotificacao.ACESSO_INICIAL, null, EMAIL, null);

        commitar();

        verify(processador).processar(notificacao.getId());
    }

    @Test
    void erroNaTentativaImediataNaoChegaAQuemChamou() {
        Notificacao notificacao = service.enfileirar(TipoNotificacao.ACESSO_INICIAL, null, EMAIL, null);
        doThrow(new IllegalStateException("banco fora")).when(processador).processar(notificacao.getId());

        assertThatCode(NotificacaoServiceTest::commitar).doesNotThrowAnyException();
    }
}
