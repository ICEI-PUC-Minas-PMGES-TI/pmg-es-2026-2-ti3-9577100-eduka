package br.pucminas.eduka.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.security.PiiEncryptionProperties;

class PessoaCryptoConverterTest {

    private static final String CHAVE_VALIDA = "u28qCTox5pjdroT6PVuzUItNOUfd1K0ySdEX/Rk7gXU=";

    private final PessoaCryptoConverter converter = new PessoaCryptoConverter(
            new PiiCryptoService(new PiiEncryptionProperties(CHAVE_VALIDA, 1)));

    @Test
    void converteERecuperaOValorOriginal() {
        byte[] cifrado = converter.convertToDatabaseColumn("Ana Silva");

        assertThat(cifrado).isNotNull();
        assertThat(converter.convertToEntityAttribute(cifrado)).isEqualTo("Ana Silva");
    }

    @Test
    void nuloPermaneceNuloNosDoisSentidos() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
