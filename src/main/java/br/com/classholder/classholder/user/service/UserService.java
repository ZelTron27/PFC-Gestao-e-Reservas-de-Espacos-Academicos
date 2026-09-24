package br.com.classholder.classholder.user.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final AuditLogRepository auditLogRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, TotpService totpService,
            AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
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

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        saveAuditLog("SENHA_ALTERADA", "USUARIO", user.getId(), email, user.getName() + " (" + user.getEmail() + ")");
        return true;
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
