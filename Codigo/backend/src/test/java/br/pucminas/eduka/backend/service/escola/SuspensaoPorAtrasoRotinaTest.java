package br.pucminas.eduka.backend.service.escola;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Rotina diaria da suspensao por fatura em atraso: o que ela roda e o que
 * acontece se falhar. As regras (prazos, quem e suspenso, falha de uma escola
 * nao parar as outras) ficam no {@link SuspensaoPorAtrasoService}; o horario,
 * no AgendaDasRotinasTest.
 */
@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class SuspensaoPorAtrasoRotinaTest {

    @Mock
    private SuspensaoPorAtrasoService service;

    @Test
    void suspendeAntesDeAvisarERegistraOResultado(CapturedOutput saida) {
        when(service.suspender()).thenReturn(2);
        when(service.avisar()).thenReturn(3);

        new SuspensaoPorAtrasoRotina(service).executar();

        // Primeiro suspende as escolas cujo prazo acabou, depois avisa as faturas novas.
        InOrder ordem = inOrder(service);
        ordem.verify(service).suspender();
        ordem.verify(service).avisar();
        assertThat(saida).contains("Rotina de suspensao: 2 escola(s) suspensa(s), 3 aviso(s) de suspensao");
    }

    @Test
    void registraARodadaMesmoSemNadaParaFazer(CapturedOutput saida) {
        // Roda uma vez por dia: a linha no log mostra que ela rodou.
        new SuspensaoPorAtrasoRotina(service).executar();

        assertThat(saida).contains("Rotina de suspensao: 0 escola(s) suspensa(s), 0 aviso(s) de suspensao");
    }

    @Test
    void falhaGeralSobeParaOAgendadorESemAvisarNaRodada() {
        // Falha de uma escola o servico ja trata; aqui a consulta inteira caiu.
        when(service.suspender()).thenThrow(new DataAccessResourceFailureException("banco fora do ar"));

        // O agendador do Spring registra o erro e roda de novo no proximo horario,
        // que pega tudo o que ficou para tras.
        assertThatThrownBy(() -> new SuspensaoPorAtrasoRotina(service).executar())
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(service, never()).avisar();
    }
}
