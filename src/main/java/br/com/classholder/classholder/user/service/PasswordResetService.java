package br.com.classholder.classholder.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.classholder.classholder.user.domain.PasswordResetToken;
import br.com.classholder.classholder.user.repository.PasswordResetTokenRepository;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long ttlMinutes;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
            @Value("${app.security.password-reset-token-ttl-minutes}") long ttlMinutes) {
        this.tokenRepository = tokenRepository;
        this.ttlMinutes = ttlMinutes;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public String createToken(Long userId) {
        tokenRepository.findByUserIdAndUsedAtIsNull(userId)
                .forEach(existing -> existing.setUsedAt(LocalDateTime.now()));

        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(hash(plainToken))
                .expiresAt(LocalDateTime.now().plusMinutes(ttlMinutes))
                .createdAt(LocalDateTime.now())
                .build();

        tokenRepository.save(token);
        return plainToken;
    }

    public Optional<Long> validateToken(String plainToken) {
        return tokenRepository.findByTokenHash(hash(plainToken))
                .filter(token -> token.getUsedAt() == null)
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(PasswordResetToken::getUserId);
    }

    public void consumeToken(String plainToken) {
        tokenRepository.findByTokenHash(hash(plainToken))
                .ifPresent(token -> {
                    token.setUsedAt(LocalDateTime.now());
                    tokenRepository.save(token);
                });
    }

    private String hash(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Erro ao gerar hash do token de redefinição de senha", e);
        }
    }

}
