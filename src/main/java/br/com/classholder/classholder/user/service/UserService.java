package br.com.classholder.classholder.user.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.dto.CreateUserRequest;
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

    public UserResponse createUser(CreateUserRequest request) {
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

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
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

    public String getOrCreateTwoFactorSecret(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (user.getTwoFactorSecret() == null) {
            String segredoEmTexto = totpService.gerarSegredo();
            user.setTwoFactorSecret(totpService.criptografar(segredoEmTexto));
            userRepository.save(user);
            return segredoEmTexto;
        }

        return totpService.descriptografar(user.getTwoFactorSecret());
    }

    public boolean confirmarConfiguracaoDoisFatores(String email, int codigo) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (user.getTwoFactorSecret() == null
                || !totpService.verificarCodigo(totpService.descriptografar(user.getTwoFactorSecret()), codigo)) {
            return false;
        }

        user.setFirstLogin(false);
        userRepository.save(user);
        return true;
    }

    public boolean verificarCodigoDoisFatores(String email, int codigo) {
        return userRepository.findByEmail(email)
                .map(user -> user.getTwoFactorSecret() != null
                        && totpService.verificarCodigo(totpService.descriptografar(user.getTwoFactorSecret()), codigo))
                .orElse(false);
    }

}
