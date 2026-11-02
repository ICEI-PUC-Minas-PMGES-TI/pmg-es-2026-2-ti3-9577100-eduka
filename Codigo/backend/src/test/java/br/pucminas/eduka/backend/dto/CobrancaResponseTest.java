package br.pucminas.eduka.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.StatusCobranca;

/** Sinal de pagamento depois do cancelamento (R23) na listagem da escola. */
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
}
