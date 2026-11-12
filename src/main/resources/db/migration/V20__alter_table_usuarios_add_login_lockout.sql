ALTER TABLE usuarios ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN locked_until TIMESTAMP;
