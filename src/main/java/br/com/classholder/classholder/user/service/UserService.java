package br.com.classholder.classholder.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.dto.UserRequest;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, TotpService totpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
    }

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

    public void acceptLgpdTerm(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        user.setLgpdAcceptedAt(LocalDateTime.now());
        userRepository.save(user);
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
            return false;
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
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
            return false;
        }

        user.setFirstLogin(false);
        userRepository.save(user);
        return true;
    }

    public boolean verifyTwoFactorCode(String email, int code) {
        return userRepository.findByEmail(email)
                .map(user -> user.getTwoFactorSecret() != null
                        && totpService.verifyCode(totpService.decrypt(user.getTwoFactorSecret()), code))
                .orElse(false);
    }

}
