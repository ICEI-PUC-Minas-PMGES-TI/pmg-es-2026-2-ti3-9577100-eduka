package br.pucminas.eduka.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import br.pucminas.eduka.backend.domain.StatusNotificacao;
import br.pucminas.eduka.backend.repository.NotificacaoRepository;

@ExtendWith(MockitoExtension.class)
class NotificacaoRotinaTest {

    private static final Instant AGORA = Instant.parse("2026-10-19T12:00:00Z");
    private static final NotificacaoProperties REGRAS = new NotificacaoProperties(6, Duration.ofMinutes(1),
            Duration.ofHours(2), 2);

    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private NotificacaoProcessador processador;

    @Test
    void tentaCadaProntaDoLoteMesmoSeUmaDerErro() {
        UUID primeira = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        when(notificacaoRepository.buscarIdsProntas(StatusNotificacao.PENDENTE, AGORA, PageRequest.of(0, 2)))
                .thenReturn(List.of(primeira, segunda));
        doThrow(new IllegalStateException("banco")).when(processador).processar(primeira);

        int tentadas = new NotificacaoRotina(notificacaoRepository, processador, REGRAS,
                Clock.fixed(AGORA, ZoneOffset.UTC)).processarFila();

        assertThat(tentadas).isEqualTo(2);
        verify(processador).processar(primeira);
        verify(processador).processar(segunda);
    }
}
