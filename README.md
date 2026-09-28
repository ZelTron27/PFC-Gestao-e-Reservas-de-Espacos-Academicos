# Projeto Reserva de Salas *Class Holder*

Plataforma web para gerenciamento de salas de uma instituição de ensino, contemplando tanto salas comuns quanto espaços que exigem supervisão, como laboratórios, salas de anatomia e auditórios.

Projeto acadêmico desenvolvido como Projeto Final de Curso (PFC).

## Sobre

O sistema tem como objetivo digitalizar e organizar o processo de reserva e solicitação de salas em uma instituição de ensino, com controle de acesso por perfil, aprovação de solicitações, prevenção de conflitos de horários e rastreabilidade.

## Metodologia de desenvolvimento

O desenvolvimento segue a metodologia Kanban, gerenciado através da aba GitHub Projects, com cartões vinculados a metas e marcos de nosso cronograma para o projeto.


# Rodando o Projeto

## Pré-requisitos

* JDK 25 (o projeto utiliza Microsoft OpenJDK 25, ms-25)
* PostgreSQL rodando localmente (ou em container)
* pgAdmin 4 (ou outro cliente SQL)
* Git
* IntelliJ IDEA (recomendado)
* Um app autenticador no celular (Google Authenticator, Microsoft Authenticator, Authy etc.), usado no 2FA

Não é necessário instalar o Maven: o projeto já inclui o Maven Wrapper ([`mvnw`](mvnw) / [`mvnw.cmd`](mvnw.cmd)).

Contas externas (gratuitas), necessárias para algumas funcionalidades:

* [Resend](https://resend.com): envio de e-mails (redefinição de senha).
* [FeriadosAPI](https://feriadosapi.com): sincronização de feriados, usados para bloquear reservas.

## Clonando o Repositório

```bash
git clone https://github.com/ZelTron27/PFC-Gestao-e-Reservas-de-Espacos-Academicos
cd PFC-Gestao-e-Reservas-de-Espacos-Academicos
```

## Configuração do Banco de Dados

Abra o pgAdmin 4 e crie um banco de dados PostgreSQL vazio chamado:

```
classholder
```

Não é necessário criar tabelas manualmente. As migrações do Flyway criam o schema automaticamente na primeira execução.

## Configuração do Ambiente

O projeto usa variáveis de ambiente para os dados sensíveis. Esses valores não ficam públicos: cada pessoa precisa criar o seu próprio arquivo `.env` na raiz do projeto (mesma pasta do [`pom.xml`](pom.xml)).

Modelo do `.env`:

```
# Banco de dados (obrigatório)
DB_URL=jdbc:postgresql://localhost:5432/classholder
DB_USERNAME=seu_usuario
DB_PASSWORD=senha_do_banco

# 2FA (obrigatório)
TOTP_SECRET_KEY=

# E-mail via Resend (obrigatório)
RESEND_API_KEY=

# URL usada nos links enviados por e-mail
APP_BASE_URL=http://localhost:8080

# Feriados (opcional, mas recomendado)
FERIADOS_API_TOKEN=
FERIADOS_CODIGO_IBGE=3550308
```

### Resumo das variáveis

| Variável | Obrigatória | Padrão | Para que serve |
|---|---|---|---|
| `DB_URL` | Sim | — | URL JDBC do PostgreSQL |
| `DB_USERNAME` | Sim | — | Usuário do PostgreSQL |
| `DB_PASSWORD` | Sim | — | Senha do PostgreSQL |
| `TOTP_SECRET_KEY` | Sim | — | Chave AES-256 (Base64) que criptografa os segredos do 2FA no banco |
| `RESEND_API_KEY` | Sim | — | Chave da API do Resend para envio de e-mails |
| `MAIL_FROM` | Não | `Class Holder <onboarding@resend.dev>` | Remetente dos e-mails |
| `APP_BASE_URL` | Não | `http://localhost:8080` | Base dos links enviados por e-mail (ex.: redefinição de senha) |
| `FERIADOS_API_TOKEN` | Não | vazio | Token da FeriadosAPI. Sem ele, a sincronização de feriados é ignorada |
| `FERIADOS_API_URL` | Não | `https://feriadosapi.com/api/v1` | Endereço base da FeriadosAPI |
| `FERIADOS_CODIGO_IBGE` | Não | `3550308` (São Paulo/SP) | Código IBGE da cidade usada para buscar feriados municipais |
| `PORT` | Não | `8080` | Porta HTTP da aplicação |

> Se alguma variável obrigatória estiver faltando, a aplicação não sobe e o Spring mostra um erro do tipo `Could not resolve placeholder 'NOME_DA_VARIAVEL'`.

### Banco de dados

`DB_USERNAME` e `DB_PASSWORD` dependem das credenciais do PostgreSQL configuradas na sua máquina. A porta padrão do PostgreSQL é `5432`. Ajuste a `DB_URL` caso use outra.

### Chave do 2FA (`TOTP_SECRET_KEY`)

Precisa ser uma chave AES-256 em Base64, gerada por você. Utilize um dos métodos abaixo:

* Git Bash:

  ```bash
  openssl rand -base64 32
  ```

* Windows PowerShell:

  ```powershell
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  $b = New-Object byte[] 32
  $rng.GetBytes($b)
  [Convert]::ToBase64String($b)
  ```

Depois de gerada, **não troque essa chave** quando já houver usuários com 2FA configurado no seu banco, pois ela é usada para decifrar os segredos já salvos. Se trocar, esses usuários não conseguirão mais validar o código.

### E-mail (`RESEND_API_KEY` e `MAIL_FROM`)

O envio de e-mails (ex.: "Esqueci minha senha") é feito pela API do Resend.

1. Crie uma conta em [resend.com](https://resend.com).
2. Em **API Keys**, gere uma chave e coloque em `RESEND_API_KEY`.
3. Para testes locais, não é preciso definir `MAIL_FROM`: o padrão já usa o remetente de testes `onboarding@resend.dev`. Esse remetente **só entrega e-mails para o endereço da própria conta do Resend**. Para enviar para qualquer destinatário, é preciso verificar um domínio próprio no Resend e usar um remetente desse domínio.

A aplicação sobe normalmente mesmo sem o envio funcionar, apenas a redefinição de senha por e-mail vai falhar.

### Feriados (`FERIADOS_API_TOKEN` e `FERIADOS_CODIGO_IBGE`)

O sistema bloqueia reservas em domingos, sábados e feriados. Os feriados (nacionais, estaduais, municipais e pontos facultativos) são obtidos da FeriadosAPI e salvos no banco:

* A sincronização roda **ao iniciar a aplicação** e depois **uma vez por mês** (dia 1, às 03:00, horário de Brasília).
* Se a API estiver fora do ar ou o token não estiver configurado, a aplicação continua funcionando com a última cópia salva no banco e apenas um aviso aparece no log.

Para configurar:

1. Crie uma conta em [feriadosapi.com](https://feriadosapi.com) e gere um token.
2. Coloque o token em `FERIADOS_API_TOKEN`.
3. Se a instituição não for de São Paulo/SP, informe em `FERIADOS_CODIGO_IBGE` o código IBGE da cidade (é possível consultá-lo no [site do IBGE](https://www.ibge.gov.br/explica/codigos-dos-municipios.php)).

## Executando o Projeto

A classe principal da aplicação é:

```
ClassholderApplication
```

### Pelo IntelliJ IDEA

1. Abra a pasta do projeto no IntelliJ. Ele reconhece o `pom.xml` e baixa as dependências automaticamente.
2. Em `Run > Edit Configurations`, selecione a configuração `ClassholderApplication` e informe o arquivo `.env` em **Environment variables**.
3. Execute a classe `ClassholderApplication`.

Na primeira execução, o IntelliJ pode solicitar a ativação do Java Annotation Processing / Lombok. Caso isso ocorra, basta habilitar quando solicitado.

### Pelo terminal (PowerShell)

O Spring Boot não lê o `.env` sozinho, então é preciso carregá-lo na sessão do terminal antes de rodar:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item "env:$($k.Trim())" $v.Trim() }
```

```powershell
.\mvnw.cmd spring-boot:run
```

### Pelo terminal (Git Bash)

```bash
set -a && source .env && set +a
```

```bash
./mvnw spring-boot:run
```

### Com Docker

O projeto possui um [`Dockerfile`](Dockerfile). O PostgreSQL continua sendo externo ao container:

```bash
docker build -t classholder .
```

```bash
docker run --env-file .env -p 8080:8080 classholder
```

> Dentro do container, `localhost` aponta para o próprio container. Se o PostgreSQL estiver rodando na sua máquina, use `host.docker.internal` na `DB_URL` (ex.: `jdbc:postgresql://host.docker.internal:5432/classholder`).

Com a aplicação rodando, acesse: [http://localhost:8080](http://localhost:8080)

## Primeiro Usuário / Admin

Não existe cadastro público. O primeiro usuário, que representa o ADMIN da instituição, precisa ser inserido manualmente no banco de dados. **Rode a aplicação pelo menos uma vez antes**, para que o Flyway crie as tabelas.

O script está em [`scripts/insert-test-user.sql`](scripts/insert-test-user.sql).

Abra o Query Tool do pgAdmin 4, acesse o banco `classholder` e execute o conteúdo desse script. Ele cria o usuário:

* **E-mail:** `adm@classholder.com`
* **Senha provisória:** `senha123` (válida apenas para o ambiente local e trocada obrigatoriamente no primeiro acesso)

### Primeiro acesso

Todo usuário, inclusive o ADMIN criado pelo script, passa por três etapas obrigatórias no primeiro login, nesta ordem. Enquanto uma delas estiver pendente, o restante do sistema fica bloqueado.

1. **Aceite do termo de LGPD.**
2. **Troca da senha inicial.** A nova senha precisa ter no mínimo 14 caracteres, com letra maiúscula, letra minúscula, número e símbolo, e deve ser diferente da atual.
3. **Configuração do 2FA.** Escaneie o QR Code com o app autenticador e informe o código gerado. **O QR Code é exibido apenas uma vez**, então salve-o no app antes de continuar.

Nos logins seguintes, o código do app autenticador é pedido sempre após a senha.

### Cadastro dos demais usuários

A partir daí, o cadastro segue a hierarquia do sistema:

1. O **ADMIN** cadastra os usuários da **coordenação**.
2. A **coordenação** cadastra professores, alunos, salas e equipamentos.

O ADMIN também tem acesso a todas as telas da coordenação.

## Licença

Projeto de caráter acadêmico, desenvolvido para o Projeto Final de Curso (PFC).
