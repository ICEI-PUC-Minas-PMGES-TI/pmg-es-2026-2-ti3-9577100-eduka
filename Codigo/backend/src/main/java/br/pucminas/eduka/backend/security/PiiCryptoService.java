package br.pucminas.eduka.backend.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Cifra/decifra dados pessoais sigilosos com AES-256-GCM. A chave nunca sai
 * da JVM: PESSOA guarda so o resultado, o Postgres nunca ve texto puro nem a
 * chave (ver docs/superpowers/specs/2026-09-24-criptografia-dados-pessoais-design.md).
 *
 * <p>Formato do valor cifrado: [versao(1 byte)][nonce(12 bytes)][ciphertext+tag].</p>
 */
@Service
@EnableConfigurationProperties(PiiEncryptionProperties.class)
public class PiiCryptoService {

    private static final int TAMANHO_CHAVE_BYTES = 32;
    private static final int TAMANHO_NONCE_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";

    private final Map<Byte, SecretKey> chavesPorVersao;
    private final byte versaoAtual;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiCryptoService(PiiEncryptionProperties properties) {
        byte[] chaveDecodificada = decodificarChave(properties.key());
        this.versaoAtual = (byte) properties.keyVersion();
        this.chavesPorVersao = Map.of(this.versaoAtual, new SecretKeySpec(chaveDecodificada, "AES"));
    }

    public byte[] cifrar(String textoPlano) {
        if (textoPlano == null) {
            return null;
        }
        byte[] nonce = new byte[TAMANHO_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.ENCRYPT_MODE, chavesPorVersao.get(versaoAtual),
                    new GCMParameterSpec(TAMANHO_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + nonce.length + ciphertext.length)
                    .put(versaoAtual)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar dado pessoal", e);
        }
    }

    public String decifrar(byte[] valorCifrado) {
        if (valorCifrado == null) {
            return null;
        }
        if (valorCifrado.length < 1 + TAMANHO_NONCE_BYTES) {
            throw new IllegalStateException("Valor cifrado corrompido: menor que o cabecalho esperado");
        }
        byte versao = valorCifrado[0];
        SecretKey chave = chavesPorVersao.get(versao);
        if (chave == null) {
            throw new IllegalStateException("Versao de chave desconhecida: " + versao);
        }
        byte[] nonce = Arrays.copyOfRange(valorCifrado, 1, 1 + TAMANHO_NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(valorCifrado, 1 + TAMANHO_NONCE_BYTES, valorCifrado.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new PiiDecryptionException("Dado cifrado invalido ou cifrado com chave diferente", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar dado pessoal", e);
        }
    }

    private static byte[] decodificarChave(String chaveBase64) {
        if (chaveBase64 == null || chaveBase64.isBlank()) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY must not be null or blank");
        }
        byte[] decodificada;
        try {
            decodificada = Base64.getDecoder().decode(chaveBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY must be valid base64", e);
        }
        if (decodificada.length != TAMANHO_CHAVE_BYTES) {
            throw new IllegalStateException(
                    "PII_ENCRYPTION_KEY must decode to exactly " + TAMANHO_CHAVE_BYTES + " bytes for AES-256");
        }
        return decodificada;
    }
}
