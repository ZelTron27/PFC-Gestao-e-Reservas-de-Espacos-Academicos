package br.com.classholder.classholder.user.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import br.com.classholder.classholder.audit.listener.LoginUserIdResolver;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@Service
public class UserDetailsServiceImpl implements UserDetailsService, LoginUserIdResolver {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());

        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.isActive(),
                locked,
                AuthorityUtils.createAuthorityList(user.getRole().getAuthority()));
    }

    @Override
    public Optional<Long> findUserIdByLogin(String login) {
        return userRepository.findByEmail(login).map(User::getId);
    }

}
