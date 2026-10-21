package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.PagamentoCobrancaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/** Rotina do lembrete de vencimento: janela no "hoje" da escola, marca antes de avisar. */
@ExtendWith(MockitoExtension.class)
class CobrancaVencimentoRotinaTest {

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

    private CobrancaService serviceEm(Instant agora) {
        return new CobrancaService(cobrancaRepository, alunoRepository, matriculaRepository,
                alunoResponsavelRepository, pessoaRepository, new IsolamentoEscola(), auditoriaService,
                avisoCobrancaService, pagamentoRepository, Clock.fixed(agora, ZoneOffset.UTC));
    }

    @Test
    void avisaAsPendentesQueVencemEmAteTresDiasSemUsuarioAutenticado() {
        // 08:00 em Sao Paulo do dia 10.
        Instant agora = Instant.parse("2026-10-10T11:00:00Z");
        UUID id = UUID.randomUUID();
        UUID escola = UUID.randomUUID();
        UUID pessoaAluno = UUID.randomUUID();
        // O driver pode devolver java.sql.Date: a conversao precisa aceitar.
        Object[] linha = { id, escola, pessoaAluno, null, "Mensalidade", new BigDecimal("850.50"),
                Date.valueOf("2026-10-13") };
        when(cobrancaRepository.buscarParaLembreteDeVencimento(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 13)))
                .thenReturn(List.<Object[]>of(linha));
        when(avisoCobrancaService.publicar(eq(AvisoCobranca.Tipo.VENCIMENTO_PROXIMO), anyList())).thenReturn(1);

        new CobrancaVencimentoRotina(serviceEm(agora)).avisar();

        InOrder ordem = inOrder(cobrancaRepository, avisoCobrancaService);
        ordem.verify(cobrancaRepository).marcarLembreteDeVencimentoEnviado(List.of(id), agora);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CobrancaParaAviso>> avisos = ArgumentCaptor.forClass(List.class);
        ordem.verify(avisoCobrancaService).publicar(eq(AvisoCobranca.Tipo.VENCIMENTO_PROXIMO), avisos.capture());
        CobrancaParaAviso aviso = avisos.getValue().getFirst();
        assertThat(aviso.id()).isEqualTo(id);
        assertThat(aviso.escolaId()).isEqualTo(escola);
        assertThat(aviso.alunoPessoaId()).isEqualTo(pessoaAluno);
        assertThat(aviso.responsavelPessoaId()).isNull();
        assertThat(aviso.dataVencimento()).isEqualTo(LocalDate.of(2026, 10, 13));
        // Rotina sem autor nao entra no log de auditoria.
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void janelaUsaODiaDaEscolaENaoODeUtc() {
        // 01:00 UTC do dia 11 ainda e 22:00 do dia 10 em Sao Paulo.
        Instant agora = Instant.parse("2026-10-11T01:00:00Z");
        when(cobrancaRepository.buscarParaLembreteDeVencimento(any(), any())).thenReturn(List.of());

        assertThat(serviceEm(agora).avisarVencimentosProximos()).isZero();

        verify(cobrancaRepository).buscarParaLembreteDeVencimento(LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 13));
        verify(cobrancaRepository, never()).marcarLembreteDeVencimentoEnviado(any(), any());
        verifyNoInteractions(avisoCobrancaService);
    }
}
