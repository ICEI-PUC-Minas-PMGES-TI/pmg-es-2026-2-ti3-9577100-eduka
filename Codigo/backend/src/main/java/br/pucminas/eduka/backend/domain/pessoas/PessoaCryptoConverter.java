package br.pucminas.eduka.backend.domain.pessoas;

import org.springframework.stereotype.Component;

import br.pucminas.eduka.backend.security.PiiCryptoService;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra/decifra os campos sigilosos de PESSOA. autoApply=false de proposito:
 * so os campos com @Convert explicito passam por aqui, para nao afetar
 * Strings de outras entidades (ex.: USUARIO.LOGIN).
 */
@Component
@Converter(autoApply = false)
public class PessoaCryptoConverter implements AttributeConverter<String, byte[]> {

    private final PiiCryptoService piiCryptoService;

    public PessoaCryptoConverter(PiiCryptoService piiCryptoService) {
        this.piiCryptoService = piiCryptoService;
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        return piiCryptoService.cifrar(attribute);
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        return piiCryptoService.decifrar(dbData);
    }
}
