ALTER TABLE salas
    ADD COLUMN permite_solicitacao_aluno BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sala_especial BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE sala_equipamentos (
    sala_id BIGINT NOT NULL REFERENCES salas (id),
    equipamento_id BIGINT NOT NULL REFERENCES equipamentos (id),
    PRIMARY KEY (sala_id, equipamento_id)
);
