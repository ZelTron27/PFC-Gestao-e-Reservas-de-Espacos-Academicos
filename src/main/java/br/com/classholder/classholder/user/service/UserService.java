package br.com.classholder.classholder.user.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.audit.aop.AuditContext;
import br.com.classholder.classholder.audit.aop.Auditable;
import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;
import br.com.classholder.classholder.shared.WhenChangesSaved;
import br.com.classholder.classholder.shared.RecursoNaoEncontradoException;
import br.com.classholder.classholder.user.UserAnonymizedEvent;
import br.com.classholder.classholder.user.UserDeactivatedEvent;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.dto.ProfileResponse;
import br.com.classholder.classholder.user.dto.UserEditRequest;
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
    private static final String ANONYMIZED_PASSWORD_HASH = "!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, TotpService totpService,
            PasswordResetService passwordResetService, EmailService emailService,
            AuditLogRepository auditLogRepository, ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
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
            throw new IllegalArgumentException("A coordenação não pode cadastrar administradores");
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
        return toResponse(saved);
    }

    public UserResponse getManageableUser(Long actorId, UserRole actorRole, Long id) {
        return toResponse(findManageableUser(actorId, actorRole, id));
    }

    @Auditable(acao = "USUARIO_ATUALIZADO", entidadeTipo = "USUARIO")
    public UserResponse updateUser(Long actorId, UserRole actorRole, Long id, UserEditRequest request) {
        User user = findManageableUser(actorId, actorRole, id);
        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new IllegalArgumentException("E-mail já cadastrado");
        }

        user.setName(request.name());
        user.setEmail(request.email());
        User saved = userRepository.save(user);
        AuditContext.setEntidadeId(saved.getId());
        return toResponse(saved);
    }

    @Transactional
    @Auditable(acao = "USUARIO_DESATIVADO", entidadeTipo = "USUARIO")
    public void deactivateUser(Long actorId, UserRole actorRole, Long id) {
        User user = findUserForStatusChange(actorId, actorRole, id);
        if (!user.isActive()) {
            throw new IllegalStateException("Esse usuário já está desativado");
        }
        ensureAnotherActiveUserRemains(user.getRole());

        user.setActive(false);
        userRepository.save(user);

        UserDeactivatedEvent event = new UserDeactivatedEvent(id);
        eventPublisher.publishEvent(event);

        List<String> cancelledReservations = event.cancelledReservations();
        String email = user.getEmail();
        String name = user.getName();
        WhenChangesSaved.run(() -> sendDeactivationEmail(id, email, name, cancelledReservations));
        AuditContext.setEntidadeId(id);
        AuditContext.setDetalhes("Reservas e solicitações canceladas: " + cancelledReservations.size());
    }

    @Transactional
    @Auditable(acao = "USUARIO_ANONIMIZADO", entidadeTipo = "USUARIO")
    public void anonymizeUser(Long actorId, UserRole actorRole, Long id) {
        User user = findUserAllowedFor(actorId, id, actorRole::canAnonymize);
        if (user.isActive()) {
            ensureAnotherActiveUserRemains(user.getRole());
        }

        UserAnonymizedEvent event = new UserAnonymizedEvent(id);
        eventPublisher.publishEvent(event);
        passwordResetService.deleteTokensOf(id);

        user.setName("Usuário excluído (ID " + id + ")");
        user.setEmail("excluido-" + id + "@anonimizado.invalid");
        user.setPasswordHash(ANONYMIZED_PASSWORD_HASH);
        user.setTwoFactorSecret(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setActive(false);
        user.setAnonymizedAt(LocalDateTime.now());
        userRepository.save(user);

        AuditContext.setEntidadeId(id);
        AuditContext.setDetalhes("Reservas e solicitações canceladas: " + event.cancelledReservations().size());
    }

    @Auditable(acao = "USUARIO_REATIVADO", entidadeTipo = "USUARIO")
    public void reactivateUser(Long actorId, UserRole actorRole, Long id) {
        User user = findUserForStatusChange(actorId, actorRole, id);
        if (user.isActive()) {
            throw new IllegalStateException("Esse usuário já está ativo");
        }

        user.setActive(true);
        userRepository.save(user);
        AuditContext.setEntidadeId(id);
    }

    public boolean isActive(String email) {
        return userRepository.findByEmail(email)
                .map(User::isActive)
                .orElse(true);
    }

    private void sendDeactivationEmail(Long id, String email, String name, List<String> cancelledReservations) {
        try {
            emailService.sendAccountDeactivatedEmail(email, name, cancelledReservations);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de desativação para o usuário {}", id, e);
        }
    }

    private User findManageableUser(Long actorId, UserRole actorRole, Long id) {
        return findUserAllowedFor(actorId, id, actorRole::canManage);
    }

    private User findUserForStatusChange(Long actorId, UserRole actorRole, Long id) {
        return findUserAllowedFor(actorId, id, actorRole::canChangeStatusOf);
    }

    private User findUserAllowedFor(Long actorId, Long id, Predicate<UserRole> allowed) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        if (id.equals(actorId)) {
            throw new IllegalArgumentException("Você não pode alterar a própria conta por aqui");
        }
        if (!allowed.test(user.getRole())) {
            throw new IllegalArgumentException("Você não tem permissão para alterar esse usuário");
        }
        if (user.isAnonymized()) {
            throw new IllegalStateException("Uma conta anonimizada não pode ser alterada nem reativada");
        }
        return user;
    }

    private void ensureAnotherActiveUserRemains(UserRole role) {
        if (role == UserRole.PROFESSOR) {
            return;
        }
        if (userRepository.findActiveByRoleForUpdate(role).size() <= 1) {
            throw new IllegalStateException(role == UserRole.ADMIN
                    ? "É preciso manter ao menos um Administrador ativo"
                    : "É preciso manter ao menos um Coordenador ativo");
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(),
                user.isAnonymized());
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        return toResponse(user);
    }

    public ProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        return new ProfileResponse(user.getName(), user.getEmail(), user.getRole().getLabel(),
                user.getLgpdAcceptedAt(), user.getTwoFactorSecret() != null);
    }

    public Map<Long, String> findUserNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (Object[] row : userRepository.findNamesByIds(ids)) {
            names.put((Long) row[0], (String) row[1]);
        }
        return names;
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        return toResponse(user);
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
            saveAuditLog("SENHA_ALTERACAO_FALHA", "USUARIO", user.getId(), user.getId());
            return false;
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da senha atual.");
        }

        validatePasswordStrength(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        saveAuditLog("SENHA_ALTERADA", "USUARIO", user.getId(), user.getId());
        return true;
    }

    private void validatePasswordStrength(String password) {
        if (password == null || !PASSWORD_POLICY.matcher(password).matches()) {
            throw new IllegalArgumentException(PASSWORD_POLICY_MESSAGE);
        }
    }

    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).filter(User::isActive).ifPresent(user -> {
            String token = passwordResetService.createToken(user.getId());
            try {
                emailService.sendPasswordResetEmail(user.getEmail(), token, passwordResetService.getTtlMinutes());
            } catch (EmailService.EmailDeliveryException e) {
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
            saveAuditLog("2FA_CONFIGURACAO_FALHA", "USUARIO", user.getId(), user.getId());
            return false;
        }

        user.setFirstLogin(false);
        userRepository.save(user);
        saveAuditLog("2FA_CONFIGURADO", "USUARIO", user.getId(), user.getId());
        return true;
    }

    public boolean verifyTwoFactorCode(String email, int code) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            saveAuditLog("ACESSO_2FA_FALHA", "USUARIO", null, null);
            return false;
        }

        boolean valido = user.getTwoFactorSecret() != null
                && totpService.verifyCode(totpService.decrypt(user.getTwoFactorSecret()), code);

        if (!valido) {
            saveAuditLog("ACESSO_2FA_FALHA", "USUARIO", user.getId(), user.getId());
            return false;
        }

        saveAuditLog("ACESSO_2FA_SUCESSO", "USUARIO", user.getId(), user.getId());
        return true;
    }

    private void saveAuditLog(String acao, String entidadeTipo, Long entidadeId, Long userId) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .acao(acao)
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
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
