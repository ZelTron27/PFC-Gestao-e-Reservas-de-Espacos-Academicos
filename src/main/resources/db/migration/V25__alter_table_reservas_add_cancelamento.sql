ALTER TABLE reservas
    ADD COLUMN cancelado_por BIGINT REFERENCES usuarios (id),
    ADD COLUMN cancelado_em TIMESTAMP;
