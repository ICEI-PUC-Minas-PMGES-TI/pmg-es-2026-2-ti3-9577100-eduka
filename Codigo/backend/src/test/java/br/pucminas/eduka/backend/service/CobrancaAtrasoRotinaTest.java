package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.pagamento.PaymentRoutingService;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/** Rotina diaria de atraso: o corte e o "hoje" da escola, nao o de UTC. */
@ExtendWith(MockitoExtension.class)
class CobrancaAtrasoRotinaTest {

    @Mock
    private CobrancaRepository cobrancaRepository;
    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private MatriculaTurmaRepository matriculaRepository;
    @Mock
    private AlunoResponsavelRepository alunoResponsavelRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private AvisoCobrancaService avisoCobrancaService;
    @Mock
    private PagamentoCobrancaRepository pagamentoRepository;
    @Mock
    private PaymentRoutingService paymentRoutingService;

    private CobrancaService serviceEm(Instant agora) {
        return new CobrancaService(cobrancaRepository, alunoRepository, matriculaRepository,
                alunoResponsavelRepository, pessoaRepository, new IsolamentoEscola(), auditoriaService,
                avisoCobrancaService, pagamentoRepository, Clock.fixed(agora, ZoneOffset.UTC));
    }

    @Test
    void rotinaMarcaAsVencidasSemUsuarioAutenticado() {
        // 00:05 em Sao Paulo, horario padrao da rotina.
        Instant agora = Instant.parse("2026-10-10T03:05:00Z");

        new CobrancaAtrasoRotina(serviceEm(agora), paymentRoutingService).marcarVencidas();

        verify(cobrancaRepository).marcarVencidasComoEmAtraso(LocalDate.of(2026, 10, 10), agora);
        // Spec §7.4: as faturas da plataforma entram na mesma rotina.
        verify(paymentRoutingService).marcarFaturasVencidas();
        // Sem autor, a rotina nao entra no log de auditoria.
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void corteUsaODiaDaEscolaENaoODeUtc() {
        // 02:00 UTC do dia 11 ainda e 23:00 do dia 10 em Sao Paulo: quem vence
        // no dia 10 nao esta em atraso.
        Instant agora = Instant.parse("2026-10-11T02:00:00Z");
        when(cobrancaRepository.marcarVencidasComoEmAtraso(LocalDate.of(2026, 10, 10), agora)).thenReturn(2);

        assertThat(serviceEm(agora).marcarVencidasComoEmAtraso()).isEqualTo(2);
    }
}
