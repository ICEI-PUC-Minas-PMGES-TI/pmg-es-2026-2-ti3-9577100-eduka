package br.pucminas.eduka.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpfValidatorTest {

    private final CpfValidator validator = new CpfValidator();

    @ParameterizedTest
    @ValueSource(strings = { "52998224725", "11144477735", "12345678909" })
    void aceitaCpfComDigitosVerificadoresCorretos(String cpf) {
        assertThat(validator.isValid(cpf, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "52998224724", "52998224715", "12345678900" })
    void rejeitaDigitoVerificadorErrado(String cpf) {
        assertThat(validator.isValid(cpf, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "00000000000", "11111111111", "99999999999" })
    void rejeitaSequenciaDeUmDigitoSo(String cpf) {
        assertThat(validator.isValid(cpf, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "529.982.247-25", "5299822472", "529982247250", "5299822472a" })
    void rejeitaFormatoForaDe11Digitos(String cpf) {
        assertThat(validator.isValid(cpf, null)).isFalse();
    }

    @Test
    void nuloFicaParaONotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
