package br.com.classholder.classholder.user.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;

@Service
public class TotpService {

    private static final String ISSUER = "Class Holder";
    private static final int QR_SIZE = 240;

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec chaveDeCriptografia;

    public TotpService(@Value("${app.security.totp-secret-key}") String chaveDeCriptografiaBase64) {
        byte[] chaveDecodificada = Base64.getDecoder().decode(chaveDeCriptografiaBase64);
        this.chaveDeCriptografia = new SecretKeySpec(chaveDecodificada, "AES");
    }

    public String gerarSegredo() {
        return googleAuthenticator.createCredentials().getKey();
    }

    public boolean verificarCodigo(String segredo, int codigo) {
        return googleAuthenticator.authorize(segredo, codigo);
    }

    public String gerarQrCodeBase64(String contaUsuario, String segredo) {
        String otpAuthUrl = construirOtpAuthUrl(contaUsuario, segredo);

        try {
            BitMatrix matrix = new QRCodeWriter().encode(otpAuthUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);

            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Erro ao gerar o QR Code de autenticação em dois fatores", e);
        }
    }

    private String construirOtpAuthUrl(String contaUsuario, String segredo) {
        String label = encode(ISSUER + ":" + contaUsuario);
        return "otpauth://totp/" + label
                + "?secret=" + segredo
                + "&issuer=" + encode(ISSUER)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public String criptografar(String segredoEmTexto) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, chaveDeCriptografia, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] textoCifrado = cipher.doFinal(segredoEmTexto.getBytes(StandardCharsets.UTF_8));

            byte[] resultado = new byte[iv.length + textoCifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(textoCifrado, 0, resultado, iv.length, textoCifrado.length);

            return Base64.getEncoder().encodeToString(resultado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao criptografar o segredo de autenticação em dois fatores", e);
        }
    }

    public String descriptografar(String segredoCriptografado) {
        try {
            byte[] dados = Base64.getDecoder().decode(segredoCriptografado);
            byte[] iv = Arrays.copyOfRange(dados, 0, GCM_IV_LENGTH_BYTES);
            byte[] textoCifrado = Arrays.copyOfRange(dados, GCM_IV_LENGTH_BYTES, dados.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, chaveDeCriptografia, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] textoPlano = cipher.doFinal(textoCifrado);

            return new String(textoPlano, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao descriptografar o segredo de autenticação em dois fatores", e);
        }
    }

}
