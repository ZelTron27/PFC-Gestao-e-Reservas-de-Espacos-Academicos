package br.com.classholder.classholder.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.warrenstrange.googleauth.GoogleAuthenticator;

class TotpServiceTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final TotpService totpService = new TotpService(KEY);

    @Test
    void shouldDecryptWhatItEncrypted() {
        String secret = totpService.generateSecret();

        String encrypted = totpService.encrypt(secret);

        assertThat(encrypted).isNotEqualTo(secret);
        assertThat(totpService.decrypt(encrypted)).isEqualTo(secret);
    }

    @Test
    void shouldUseADifferentIvOnEachEncryption() {
        String secret = totpService.generateSecret();

        assertThat(totpService.encrypt(secret)).isNotEqualTo(totpService.encrypt(secret));
    }

    @Test
    void shouldRejectTamperedCiphertext() {
        byte[] data = Base64.getDecoder().decode(totpService.encrypt(totpService.generateSecret()));
        data[data.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(data);

        assertThrows(IllegalStateException.class, () -> totpService.decrypt(tampered));
    }

    @Test
    void shouldRejectCiphertextFromAnotherKey() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        TotpService otherService = new TotpService(Base64.getEncoder().encodeToString(otherKey));
        String encrypted = otherService.encrypt(totpService.generateSecret());

        assertThrows(IllegalStateException.class, () -> totpService.decrypt(encrypted));
    }

    @Test
    void shouldAcceptTheCurrentCodeAndRefuseAWrongOne() {
        String secret = totpService.generateSecret();
        int currentCode = new GoogleAuthenticator().getTotpPassword(secret);

        assertThat(totpService.verifyCode(secret, currentCode)).isTrue();
        assertThat(totpService.verifyCode(secret, (currentCode + 1) % 1_000_000)).isFalse();
    }

    @Test
    void shouldGenerateAPngQrCode() {
        String qrCode = totpService.generateQrCodeBase64("professor@classholder.com", totpService.generateSecret());

        byte[] png = Base64.getDecoder().decode(qrCode);
        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }

}
