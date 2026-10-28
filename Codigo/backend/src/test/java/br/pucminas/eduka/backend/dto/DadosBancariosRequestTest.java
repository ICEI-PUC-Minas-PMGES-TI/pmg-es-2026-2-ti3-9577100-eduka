package br.pucminas.eduka.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.pagamento.tipos.TipoContaBancaria;

/** TST-U22 (R7): os dados bancarios da requisicao nao aparecem no toString. */
class DadosBancariosRequestTest {

    @Test
    void toStringMostraSoOCodigoDoBanco() {
        DadosBancariosRequest dados = new DadosBancariosRequest("341", "1234", "987654321", "7",
                TipoContaBancaria.POUPANCA);

        assertThat(dados.toString())
                .contains("341")
                .doesNotContain("1234")
                .doesNotContain("987654321")
                .doesNotContain("=7");
    }
}
