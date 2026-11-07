package br.com.classholder.classholder.user.dto;

import java.time.LocalDateTime;

public record ProfileResponse(String name, String email, String roleLabel, LocalDateTime lgpdAcceptedAt,
        boolean twoFactorConfigured) {

}
