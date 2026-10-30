ALTER TABLE reservas RENAME COLUMN professor_id TO responsavel_id;

ALTER TABLE reservas
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now(),
    ADD COLUMN motivo_cancelamento VARCHAR(40),
    ADD COLUMN check_in_at TIMESTAMP,
    ADD COLUMN avaliado_por BIGINT REFERENCES usuarios (id),
    ADD COLUMN avaliado_em TIMESTAMP,
    ADD COLUMN justificativa_recusa VARCHAR(500);
