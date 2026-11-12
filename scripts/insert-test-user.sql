-- Usuário ADM para criação dos demais usuarios.
-- Senha em texto puro: senha123
-- Hash abaixo gerado com BCryptPasswordEncoder (mesma classe usada em PasswordEncoderConfig).
INSERT INTO usuarios (name, email, password_hash, role)
VALUES
    ('Usuario ADM', 'adm@classholder.com', '$2a$10$6dAXqy9r60R8FZtQh2HKXumnVSyl2nfp1SEfr8AAA4F8Tyofa7w9.', 'ADMIN');

