ALTER TABLE audit_log ADD COLUMN usuario_id BIGINT REFERENCES usuarios (id);

UPDATE audit_log
SET usuario_id = (SELECT u.id FROM usuarios u WHERE u.email = audit_log.usuario_email);

UPDATE audit_log
SET entidade_nome = NULL
WHERE entidade_tipo = 'USUARIO';

UPDATE audit_log
SET detalhes = NULLIF(TRIM(REGEXP_REPLACE(detalhes, '(^|[\s;,]+)IP: \S*', '', 'g')), '')
WHERE detalhes LIKE '%IP: %';

ALTER TABLE audit_log DROP COLUMN usuario_email;
