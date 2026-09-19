CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuarios(id),
    usuario_email VARCHAR(150) NOT NULL,
    acao VARCHAR(50) NOT NULL,
    entidade_tipo VARCHAR(50),
    entidade_id BIGINT,
    timestamp TIMESTAMP NOT NULL DEFAULT now(),
    detalhes TEXT
);
