package br.pucminas.eduka.backend.pagamento.tipos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** TST-U22 (R7, R8): toString dos tipos do provedor nao expoe segredo. */
class TiposProvedorToStringTest {

    @Test
    void credencialNaoApareceNoToString() {
        CredencialProvedor credencial = new CredencialProvedor("chave-secreta-da-escola");

        assertThat(credencial.toString()).doesNotContain("chave-secreta");
    }

    @Test
    void dadosBancariosMostramSoOCodigoDoBanco() {
        DadosBancarios dados = new DadosBancarios("341", "1234", "987654321", "7", TipoContaBancaria.CORRENTE);

        assertThat(dados.toString())
                .contains("341")
                .doesNotContain("1234")
                .doesNotContain("987654321")
                .doesNotContain("=7");
    }

    @Test
    void pagadorNaoMostraCpfNemEmail() {
        DadosPagador pagador = new DadosPagador("EDK-PES-1", "Ana", "12345678909", "ana@example.com");

        assertThat(pagador.toString()).doesNotContain("12345678909").doesNotContain("ana@example.com");
    }

    @Test
    void credencialVaziaNaoEstaPreenchida() {
        assertThat(new CredencialProvedor(null).preenchida()).isFalse();
        assertThat(new CredencialProvedor(" ").preenchida()).isFalse();
        assertThat(new CredencialProvedor("x").preenchida()).isTrue();
    }
}
