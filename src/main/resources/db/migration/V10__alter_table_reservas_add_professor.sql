ALTER TABLE reservas
    ADD COLUMN professor_id BIGINT NOT NULL REFERENCES usuarios (id);
