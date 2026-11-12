package br.com.classholder.classholder.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.classholder.classholder.user.domain.PasswordResetToken;
import br.com.classholder.classholder.user.repository.PasswordResetTokenRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final long TTL_MINUTES = 30;
    private static final Long USER_ID = 42L;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, TTL_MINUTES);
    }

    @Test
    void shouldReturnUserIdWhenTokenIsValidAndUnused() {
        PasswordResetToken token = token(null, LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        Optional<Long> result = service.validateToken("plain-token");

        assertEquals(Optional.of(USER_ID), result);
    }

    @Test
    void shouldRejectTokenWhenItWasAlreadyUsed() {
        PasswordResetToken token = token(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        Optional<Long> result = service.validateToken("plain-token");

        assertEquals(Optional.empty(), result);
    }

    @Test
    void shouldRejectTokenWhenItIsExpired() {
        PasswordResetToken token = token(null, LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        Optional<Long> result = service.validateToken("plain-token");

        assertEquals(Optional.empty(), result);
    }

    @Test
    void shouldInvalidatePreviousTokensAndSaveNewOneWhenCreatingToken() {
        PasswordResetToken openOne = token(null, LocalDateTime.now().plusMinutes(5));
        PasswordResetToken openTwo = token(null, LocalDateTime.now().plusMinutes(20));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(List.of(openOne, openTwo));
        LocalDateTime before = LocalDateTime.now();

        String plainToken = service.createToken(USER_ID);

        assertNotNull(openOne.getUsedAt());
        assertNotNull(openTwo.getUsedAt());
        assertFalse(plainToken.isBlank());
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, times(1)).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertTrue(saved.getExpiresAt().isAfter(before.plusMinutes(29)));
        assertTrue(saved.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(31)));
    }

    private PasswordResetToken token(LocalDateTime usedAt, LocalDateTime expiresAt) {
        return PasswordResetToken.builder()
                .userId(USER_ID)
                .tokenHash("hash")
                .usedAt(usedAt)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();
    }

}