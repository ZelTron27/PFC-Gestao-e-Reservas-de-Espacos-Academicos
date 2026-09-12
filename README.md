# Projeto Reserva de Salas *Class Holder*

Plataforma web para gerenciamento de salas de uma instituição de ensino, contemplando tanto salas comuns quanto espaços que exigem supervisão, como laboratórios, salas de anatomia e auditórios.

Projeto acadêmico desenvolvido como Projeto Final de Curso (PFC).

## Sobre

O sistema tem como objetivo digitalizar e organizar o processo de reserva e solicitação de salas em uma instituição de enisno, com controle de acesso por perfil, aprovação de solicitações e prevenção de conflitos de horários e rastreabilidade.

## Metodologia de desenvolvimento

O desenvolvimento segue a metodologia Kanban, gerenciado através da aba GitHub Projects, com cartões vinculados a metas e marcos de nosso cronograma para o projeto.


# Rodando o Projeto

## Pré-requisitos

* IntelliJ IDEA
* JDK 25 (o projeto utiliza Microsoft OpenJDK 25, ms-25)
* PostgreSQL rodando localmente (ou em container)
* pgAdmin 4
* Git

## Clonando o Repositório

```bash
git clone https://github.com/ZelTron27/PFC-Gestao-e-Reservas-de-Espacos-Academicos
cd classholder/classholder
```

## Configuração do Banco de Dados

Abra o pgAdmin 4 e crie um banco de dados PostgreSQL vazio chamado:

```
classholder
```

Não é necessário criar tabelas manualmente. As migrações do Flyway criam o schema automaticamente na primeira execução.

## Configuração do Ambiente

O projeto usa variáveis de ambiente para os dados sensíveis (conexão com o banco e chave de criptografia do 2FA). Esses valores não ficam púlbicos, cada pessoa precisa criar o seu próprio arquivo `.env` na pasta `/classholder`, com as seguintes variáveis:

```
DB_URL=jdbc:postgresql://localhost:****/classholder
DB_USERNAME=seu_usuario
DB_PASSWORD=senha_do_banco
TOTP_SECRET_KEY=
```

* `DB_USERNAME` e `DB_PASSWORD` dependem das credenciais do PostgreSQL configuradas na sua máquina.
    * `TOTP_SECRET_KEY` precisa ser uma chave AES-256 em Base64, gerada por você, para isso, utilize um dos métodos abaixo:
        * Git Bash: Insira o comando:`openssl rand -base64 32`
        * Windows PowerShell:

          ```
          $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
          $b = New-Object byte[] 32
          $rng.GetBytes($b)
          [Convert]::ToBase64String($b)
          ```

Depois de gerada, evite trocar essa chave assim que usuários reais tiverem configurado o 2FA no seu banco, já que ela é usada para decifrar os já salvos.

## Carregando as Variáveis de Ambiente

O Spring Boot precisa carregar essas variáveis a partir do `.env` ao rodar o projeto:

* Pela IDE (IntelliJ): em `Run > Edit Configurations`, selecione a configuração da aplicação `ClassholderApplication` e informe o `.env` em Environment variables.

Também é possível que, na primeira execução, o IntelliJ solicite a ativação do Java Annotation Processing / Lombok. Caso isso ocorra, basta habilitar o suporte ao Lombok quando solicitado.

## Executando o Projeto

A classe principal da aplicação é:

```
ClassholderApplication
```

Execute essa classe pelo IntelliJ IDEA normalmente.

## Primeiro Usuário / Admin

O primeiro usuário, que representa o ADMIN da instituição, precisa ser cadastrado manualmente no banco de dados. O script para isso está localizado em:

```
ClassHolder/scripts/insert-test-user.sql
```

Abra o Query Tool do pgAdmin 4, acesse o banco `classholder` e execute o conteúdo desse script.

Depois disso, basta acessar o sistema e realizar o login com o usuário criado pelo script.

## Licença

Projeto de caráter acadêmico, desenvolvido para o Projeto Final de Curso (PFC).