package br.com.classholder.classholder.audit.listener;

import java.util.Optional;

public interface LoginUserIdResolver {

    Optional<Long> findUserIdByLogin(String login);

}
