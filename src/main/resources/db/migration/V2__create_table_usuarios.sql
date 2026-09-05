CREATE TABLE usuarios (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    first_login BOOLEAN NOT NULL DEFAULT TRUE,
    lgpd_accepted_at TIMESTAMP,
    two_factor_secret VARCHAR(255)
);
