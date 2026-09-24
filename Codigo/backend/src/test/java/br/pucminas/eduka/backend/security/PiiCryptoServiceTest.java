package br.pucminas.eduka.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PiiCryptoServiceTest {

    private static final String CHAVE_VALIDA = "u28qCTox5pjdroT6PVuzUItNOUfd1K0ySdEX/Rk7gXU=";
    private static final String OUTRA_CHAVE_VALIDA = "jv8dBgnsR8ORKINtoLSfwO9OZko6Ny5TXWFg2n3KZi4=";

    private final PiiCryptoService service = new PiiCryptoService(new PiiEncryptionProperties(CHAVE_VALIDA, 1));

    @Test
    void cifrarEDecifrarFazemRoundTrip() {
        byte[] cifrado = service.cifrar("Ana Silva");

        assertThat(service.decifrar(cifrado)).isEqualTo("Ana Silva");
    }

    @Test
    void duasChamadasParaOMesmoTextoProduzemCiphertextsDiferentes() {
        byte[] primeiro = service.cifrar("Ana Silva");
        byte[] segundo = service.cifrar("Ana Silva");

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    void nuloPermaneceNuloNosDoisSentidos() {
        assertThat(service.cifrar(null)).isNull();
        assertThat(service.decifrar(null)).isNull();
    }

    @Test
    void decifrarComChaveDiferenteFalha() {
        byte[] cifrado = service.cifrar("Ana Silva");
        PiiCryptoService outroServico = new PiiCryptoService(new PiiEncryptionProperties(OUTRA_CHAVE_VALIDA, 1));

        assertThatThrownBy(() -> outroServico.decifrar(cifrado))
                .isInstanceOf(PiiDecryptionException.class);
    }

    @Test
    void decifrarVersaoDesconhecidaFalha() {
        byte[] cifrado = service.cifrar("Ana Silva");
        cifrado[0] = 99;

        assertThatThrownBy(() -> service.decifrar(cifrado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Versao de chave desconhecida");
    }

    @Test
    void decifrarValorCorrompidoMenorQueOCabecalhoFalhaControladamente() {
        assertThatThrownBy(() -> service.decifrar(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chaveComTamanhoErradoFalhaNaConstrucao() {
        assertThatThrownBy(() -> new PiiCryptoService(new PiiEncryptionProperties("Y3VydGEK", 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chaveNulaFalhaNaConstrucao() {
        assertThatThrownBy(() -> new PiiCryptoService(new PiiEncryptionProperties(null, 1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
