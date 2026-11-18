package br.pucminas.eduka.backend.dto.financeiro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;

/** Sinais que a listagem da escola leva prontos: pago apos cancelar (R23) e se ainda edita (R24). */
class CobrancaResponseTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);
    private static final Instant CANCELADA_EM = Instant.parse("2026-10-15T12:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "PAGA,      true,  true",
            "PAGA,      false, false",
            "CANCELADA, true,  false",
            "PENDENTE,  false, false" })
    void pagaAposCancelamentoSoQuandoPagaTemDataDeCancelamento(StatusCobranca status, boolean foiCancelada,
            boolean esperado) {
        Cobranca cobranca = Cobranca.builder().id(UUID.randomUUID()).aluno(Aluno.builder().build())
                .descricao("Mensalidade").valor(new BigDecimal("500.00")).dataVencimento(HOJE.plusDays(5))
                .status(status).canceladaEm(foiCancelada ? CANCELADA_EM : null).build();

        CobrancaResponse resposta = CobrancaResponse.de(cobranca, null, null, HOJE);

        assertThat(resposta.pagaAposCancelamento()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDENTE,  NAO_EMITIDA,   true",
            "EM_ATRASO, FALHA_EMISSAO, true",
            "PENDENTE,  EMITINDO,      false",
            "PENDENTE,  EMITIDA,       false",
            "PAGA,      NAO_EMITIDA,   false",
            "CANCELADA, NAO_EMITIDA,   false" })
    void editavelSoEmAbertoAntesDoPagar(StatusCobranca status, SituacaoProvedor situacao, boolean esperado) {
        Cobranca cobranca = Cobranca.builder().id(UUID.randomUUID()).aluno(Aluno.builder().build())
                .descricao("Mensalidade").valor(new BigDecimal("500.00")).dataVencimento(HOJE.plusDays(5))
                .status(status).situacaoProvedor(situacao).build();

        assertThat(CobrancaResponse.de(cobranca, null, null, HOJE).editavel()).isEqualTo(esperado);
    }
}
