package br.com.classholder.classholder.user.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.classholder.classholder.audit.aop.AuditContext;
import br.com.classholder.classholder.audit.aop.Auditable;
import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.dto.UserRequest;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.repository.UserRepository;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public static final String PASSWORD_POLICY_MESSAGE =
            "A senha deve ter no mínimo 14 caracteres e conter letra maiúscula, minúscula, número e símbolo.";

    private static final Pattern PASSWORD_POLICY = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{14,}$");

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final AuditLogRepository auditLogRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, TotpService totpService,
            PasswordResetService passwordResetService, EmailService emailService,
            AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.passwordResetService = passwordResetService;
        this.emailService = emailService;
        this.auditLogRepository = auditLogRepository;
    }

    @Auditable(acao = "USUARIO_CRIADO", entidadeTipo = "USUARIO")
    public UserResponse createUser(UserRole creatorRole, UserRequest request) {
        if (creatorRole != UserRole.ADMIN && request.role() == UserRole.ADMIN) {
            throw new IllegalArgumentException("Coordenação não pode criar usuários administradores");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado");
        }

        validatePasswordStrength(request.password());

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        User saved = userRepository.save(user);
        AuditContext.setEntidadeId(saved.getId());
        AuditContext.setNome(saved.getName() + " (" + saved.getEmail() + ")");
        return new UserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole());
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(user -> new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole()))
                .toList();
    }

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public boolean hasAcceptedLgpdTerm(String email) {
        return userRepository.findByEmail(email)
                .map(user -> user.getLgpdAcceptedAt() != null)
                .orElse(false);
    }

    @Auditable(acao = "LGPD_ACEITO", entidadeTipo = "USUARIO")
    public void acceptLgpdTerm(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        user.setLgpdAcceptedAt(LocalDateTime.now());
        userRepository.save(user);
        AuditContext.setEntidadeId(user.getId());
        AuditContext.setNome(user.getName() + " (" + user.getEmail() + ")");
    }

    public boolean isFirstLogin(String email) {
        return userRepository.findByEmail(email)
                .map(User::isFirstLogin)
                .orElse(true);
    }

    public boolean mustChangePassword(String email) {
        return userRepository.findByEmail(email)
                .map(User::isMustChangePassword)
                .orElse(false);
    }

    public boolean changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            saveAuditLog("SENHA_ALTERACAO_FALHA", "USUARIO", user.getId(), email,
                    user.getName() + " (" + user.getEmail() + ")");
            return false;
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da senha atual.");
        }

        validatePasswordStrength(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        saveAuditLog("SENHA_ALTERADA", "USUARIO", user.getId(), email, user.getName() + " (" + user.getEmail() + ")");
        return true;
    }

    private void validatePasswordStrength(String password) {
        if (password == null || !PASSWORD_POLICY.matcher(password).matches()) {
            throw new IllegalArgumentException(PASSWORD_POLICY_MESSAGE);
        }
    }

    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = passwordResetService.createToken(user.getId());
            try {
                emailService.sendPasswordResetEmail(user.getEmail(), token, passwordResetService.getTtlMinutes());
            } catch (MailException e) {
                log.error("Falha ao enviar e-mail de redefinição de senha para o usuário {}", user.getId(), e);
            }
        });
    }

    public boolean isPasswordResetTokenValid(String token) {
        return passwordResetService.validateToken(token).isPresent();
    }

    public boolean resetPassword(String token, String newPassword) {
        Long userId = passwordResetService.validateToken(token).orElse(null);
        if (userId == null) {
            return false;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da senha atual.");
        }

        validatePasswordStrength(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        passwordResetService.consumeToken(token);
        return true;
    }

    public boolean isAccountLocked(String email) {
        return userRepository.findByEmail(email)
                .map(user -> user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    public void registerFailedLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                return;
            }

            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            } else {
                user.setFailedLoginAttempts(attempts);
            }
            userRepository.save(user);
        });
    }

    public void registerSuccessfulLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });
    }

    public String getOrCreateTwoFactorSecret(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (user.getTwoFactorSecret() == null) {
            String plainSecret = totpService.generateSecret();
            user.setTwoFactorSecret(totpService.encrypt(plainSecret));
            userRepository.save(user);
            return plainSecret;
        }

        return totpService.decrypt(user.getTwoFactorSecret());
    }

    public boolean confirmTwoFactorSetup(String email, int code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (user.getTwoFactorSecret() == null
                || !totpService.verifyCode(totpService.decrypt(user.getTwoFactorSecret()), code)) {
            saveAuditLog("2FA_CONFIGURACAO_FALHA", "USUARIO", user.getId(), email,
                    user.getName() + " (" + user.getEmail() + ")");
            return false;
        }

        user.setFirstLogin(false);
        userRepository.save(user);
        saveAuditLog("2FA_CONFIGURADO", "USUARIO", user.getId(), email, user.getName() + " (" + user.getEmail() + ")");
        return true;
    }

    public boolean verifyTwoFactorCode(String email, int code) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            saveAuditLog("ACESSO_2FA_FALHA", "USUARIO", null, email, null);
            return false;
        }

        boolean valido = user.getTwoFactorSecret() != null
                && totpService.verifyCode(totpService.decrypt(user.getTwoFactorSecret()), code);

        if (!valido) {
            saveAuditLog("ACESSO_2FA_FALHA", "USUARIO", user.getId(), email,
                    user.getName() + " (" + user.getEmail() + ")");
            return false;
        }

        saveAuditLog("ACESSO_2FA_SUCESSO", "USUARIO", user.getId(), email,
                user.getName() + " (" + user.getEmail() + ")");
        return true;
    }

    private void saveAuditLog(String acao, String entidadeTipo, Long entidadeId, String usuarioEmail,
            String entidadeNome) {
        AuditLog auditLog = AuditLog.builder()
                .usuarioEmail(usuarioEmail)
                .acao(acao)
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
                .entidadeNome(entidadeNome)
                .timestamp(Instant.now())
                .build();

        try {
            auditLogRepository.save(auditLog);
        } catch (RuntimeException e) {
            log.error("Falha ao gravar log de auditoria: acao={}, entidadeTipo={}, entidadeId={}",
                    acao, entidadeTipo, entidadeId, e);
        }
    }

}
