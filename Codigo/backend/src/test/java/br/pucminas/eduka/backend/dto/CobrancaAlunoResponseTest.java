package br.pucminas.eduka.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Cobranca;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;

/** Situacao do pagamento e acoes que o portal do aluno recebe (spec §10.2). */
class CobrancaAlunoResponseTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);

    @ParameterizedTest
    @CsvSource({
            "PENDENTE,  NAO_EMITIDA,   2026-10-25, PENDENTE,  AGUARDANDO_PAGAMENTO, PAGAR",
            "PENDENTE,  FALHA_EMISSAO, 2026-10-25, PENDENTE,  AGUARDANDO_PAGAMENTO, PAGAR",
            "PENDENTE,  EMITIDA,       2026-10-25, PENDENTE,  AGUARDANDO_PAGAMENTO, PAGAR",
            "PENDENTE,  EMITINDO,      2026-10-25, PENDENTE,  PROCESSANDO,          PAGAR",
            "PENDENTE,  EMITIDA,       2026-10-10, EM_ATRASO, AGUARDANDO_PAGAMENTO, PAGAR",
            "EM_ATRASO, EMITIDA,       2026-10-10, EM_ATRASO, AGUARDANDO_PAGAMENTO, PAGAR",
            "PAGA,      EMITIDA,       2026-10-10, PAGA,      PAGA,                 ''",
            "CANCELADA, NAO_EMITIDA,   2026-10-25, CANCELADA, CANCELADA,            ''" })
    void situacaoEAcoesSaemDoStatusEDaSituacaoNoProvedor(StatusCobranca gravado, SituacaoProvedor provedor,
            LocalDate vencimento, String status, String situacaoPagamento, String acao) {
        Cobranca cobranca = Cobranca.builder().id(UUID.randomUUID()).aluno(Aluno.builder().build())
                .descricao("Mensalidade").valor(new BigDecimal("500.00")).dataVencimento(vencimento).status(gravado)
                .situacaoProvedor(provedor).formaPagamento(FormaPagamento.PIX).build();

        CobrancaAlunoResponse resposta = CobrancaAlunoResponse.de(cobranca, HOJE);

        assertThat(resposta.status()).isEqualTo(status);
        assertThat(resposta.situacaoPagamento()).isEqualTo(situacaoPagamento);
        assertThat(resposta.acoes()).isEqualTo(acao.isEmpty() ? List.of() : List.of(acao));
        assertThat(resposta.formaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(resposta.pagamento()).isNull();
    }
}
