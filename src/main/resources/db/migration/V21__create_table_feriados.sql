CREATE TABLE feriados (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data DATE NOT NULL UNIQUE,
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    sincronizado_em TIMESTAMP NOT NULL
);

CREATE TABLE sincronizacoes_feriados (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    executada_em TIMESTAMP NOT NULL,
    gatilho VARCHAR(20) NOT NULL,
    sucesso BOOLEAN NOT NULL,
    fonte VARCHAR(50) NOT NULL,
    anos VARCHAR(50) NOT NULL,
    total_feriados INTEGER,
    mensagem TEXT
);

CREATE INDEX idx_sincronizacoes_feriados_executada_em ON sincronizacoes_feriados(executada_em);
