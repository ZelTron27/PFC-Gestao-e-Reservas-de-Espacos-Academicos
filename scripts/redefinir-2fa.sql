-- Redefinição do 2FA de um usuário que perdeu o aplicativo autenticador.
-- Execute no Query Tool do pgAdmin 4, conectado ao banco da aplicação.

-- Passo 1: localizar o campo a baixo:
--    v_email     text   := 'email@exemplo.com';
--    v_confirmar text   := '';

-- Passo 2: subistitua o email@exemplo.com no v_email que está dentro das aspas simples ' ' ( mas não apague as aspas simples ), e coloque o email do usuario que será redefinido o 2FA e execute o script. Nada é alterado ainda, vai mostrar o nome, o perfil e o ID do usuário.
-- Passo 3: confira se é a pessoa certa, no v_confirmar preencha dentro das aspas simples com o ID mostrado e execute o script de novo. O 2FA só é redefinido se v_confirmar for igual ao ID do usuário.

-- No próximo login, o usuário configura um novo QR Code. A senha não é alterada. A operação fica registrada na auditoria como "2FA_REDEFINIDO".

DO $$
DECLARE
    v_email     text   := 'email@exemplo.com';
    v_confirmar text   := '';

    v_usuario   usuarios%ROWTYPE;
BEGIN
    SELECT * INTO v_usuario FROM usuarios WHERE email = v_email;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Nenhum usuário encontrado com o e-mail "%". Nada foi alterado.', v_email;
    END IF;

    IF v_usuario.anonimizado_em IS NOT NULL THEN
        RAISE EXCEPTION 'A conta de ID % está anonimizada e não pode ser redefinida. Nada foi alterado.', v_usuario.id;
    END IF;

    RAISE NOTICE 'Usuário: % - Perfil: % - E-mail: % - ID: %',
        v_usuario.name, v_usuario.role, v_usuario.email, v_usuario.id;

    IF btrim(v_confirmar) = '' THEN
        RAISE NOTICE 'Nada foi alterado. Para redefinir o 2FA, preencha v_confirmar com o ID % e execute de novo.', v_usuario.id;
        RETURN;
    END IF;

    IF btrim(v_confirmar) <> v_usuario.id::text THEN
        RAISE EXCEPTION 'O ID informado em v_confirmar (%) não confere com o ID do usuário (%). Nada foi alterado.',
            v_confirmar, v_usuario.id;
    END IF;

    UPDATE usuarios
    SET two_factor_secret = NULL,
        first_login = TRUE,
        failed_login_attempts = 0,
        locked_until = NULL
    WHERE id = v_usuario.id;

    INSERT INTO audit_log (acao, entidade_tipo, entidade_id, timestamp, detalhes)
    VALUES ('2FA_REDEFINIDO', 'USUARIO', v_usuario.id, now() AT TIME ZONE 'America/Sao_Paulo',
            'Redefinição do 2FA pelo script do manual de implantação');

    RAISE NOTICE 'Concluído: o 2FA do usuário % (ID %) foi redefinido. No próximo login ele configura um novo QR Code.',
        v_usuario.name, v_usuario.id;
END $$;
