## 1. Identificação
- **Aluno(s):** Luis Augusto Berlinck de Camargo - 1125140506
- **Aluno(s):** Matheus Martins de Souza - 11251401900
- **Projeto (PFC):** Class Holder
- **Branch:** feat/testes-automatizados

## 2. Resumo da entrega
Foram criados 12 testes unitários com JUnit 5 e Mockito para UserService (cadastro, senha forte e bloqueio de login), 
ReservationService (criação, conflito e cancelamento de reservas) e 
PasswordResetService (validação de tokens). Gestão de usuários, autenticação e reservas passaram a ter 
cobertura das regras centrais.

## 3. Cenários de testes unitários implementados
| # | Classe testada | Método / regra | Cenário | Tipo | Arquivo de teste | Método de teste |
|---|----------------|----------------|---------|------|------------------|-----------------|
| 1 | UserService | createUser() | ADM cria Professor com dados válidos e o usuário é salvo com a senha criptografada | Feliz | UserServiceTest | shouldCreateProfessorWhenDataIsValid |
| 2 | UserService | createUser() | E-mail já cadastrado lança exceção e nenhum usuário é salvo | Violação | UserServiceTest | shouldThrowExceptionWhenEmailIsAlreadyRegistered |
| 3 | UserService | createUser() / política de senha | Senha com 13 caracteres ou sem maiúscula, minúscula, número ou símbolo é recusada (parametrizado) | Limite | UserServiceTest | shouldRejectPasswordWhenItDoesNotMeetStrongPasswordPolicy |
| 4 | UserService | registerFailedLogin() | 5ª falha de login bloqueia a conta por 15 minutos e zera o contador | Limite | UserServiceTest | shouldLockAccountFor15MinutesWhenFifthLoginFails |
| 5 | ReservationService | reserveCommonRoom() | Sala comum ativa, horário livre e dentro do funcionamento: reserva é salva como CONFIRMADA | Feliz | ReservationServiceTest | shouldCreateReservationWhenRoomIsFreeAndWithinOperatingHours |
| 6 | ReservationService | reserveCommonRoom() | Conflito com reserva confirmada lança exceção e nada é salvo | Violação | ReservationServiceTest | shouldThrowWhenConfirmedReservationAlreadyExistsAtThatTime |
| 7 | ReservationService | reserveCommonRoom() | Início igual ou posterior ao fim lança exceção (parametrizado) | Limite | ReservationServiceTest | shouldThrowWhenStartIsNotBeforeEnd |
| 8 | ReservationService | cancelReservation() | Reserva confirmada passa para CANCELADA e é salva | Feliz | ReservationServiceTest | shouldCancelConfirmedReservation |
| 9 | PasswordResetService | validateToken() | Token válido e não usado retorna o ID do usuário | Feliz | PasswordResetServiceTest | shouldReturnUserIdWhenTokenIsValidAndUnused |
| 10 | PasswordResetService | validateToken() | Token já utilizado é recusado | Violação | PasswordResetServiceTest | shouldRejectTokenWhenItWasAlreadyUsed |
| 11 | PasswordResetService | validateToken() | Token expirado há 1 minuto é recusado | Limite | PasswordResetServiceTest | shouldRejectTokenWhenItIsExpired |
| 12 | PasswordResetService | createToken() | Novo token invalida os tokens anteriores em aberto e é salvo | Feliz | PasswordResetServiceTest | shouldInvalidatePreviousTokensAndSaveNewOneWhenCreatingToken |

Tipo: Feliz | Violação | Limite
**Total de cenários unitários:** 12

## 4. Cenários de testes de integração implementados
| # | Camadas envolvidas | Cenário | Arquivo de teste | Método de teste | Recurso usado |
|---|--------------------|---------|------------------|-----------------|---------------|
| 1 | Controller + Security + Interceptor + Service + BD | GET /reservas retorna 200 com a view reservas/lista e o HTML com a sala e a finalidade da reserva do professor | ReservationControllerIntegrationTest | shouldReturn200AndListProfessorReservationsWhenProfessorAccessesReservations | @SpringBootTest + MockMvc + H2 |
| 2 | Controller + Security | Professor acessa GET /reservas/todas e recebe 403, sem renderizar a view e sem vazar dados | ReservationControllerIntegrationTest | shouldReturn403WhenProfessorAccessesAllReservations | @SpringBootTest + MockMvc + H2 |
| 3 | Repository + BD | findConflicting encontra reserva com sobreposição de horário | ReservationRepositoryIntegrationTest | shouldFindConflictWhenIntervalsOverlap | @DataJpaTest + H2 |
| 4 | Repository + BD | findConflicting não acusa conflito quando o novo horário começa exatamente no fim do existente | ReservationRepositoryIntegrationTest | shouldNotFindConflictWhenNewReservationStartsExactlyWhenExistingEnds | @DataJpaTest + H2 |
| 5 | Controller + Security + Service + BD | POST /reservas cria a reserva, redireciona para /reservas, a reserva é gravada no banco e aparece no GET /reservas | ReservationFlowIntegrationTest | shouldPersistAndDisplayReservationWhenProfessorCreatesIt | @SpringBootTest + MockMvc + H2 |

**Total de cenários de integração:** 5

## 5. Arquivos de teste criados ou alterados
| Arquivo (caminho completo) | Criado / Alterado | Qtd. de testes |
|----------------------------|-------------------|----------------|
| src/test/java/br/com/classholder/classholder/user/service/UserServiceTest.java | Criado | 4 |
| src/test/java/br/com/classholder/classholder/user/service/PasswordResetServiceTest.java | Criado | 4 |
| src/test/java/br/com/classholder/classholder/reservation/service/ReservationServiceTest.java | Criado | 8 |
| src/test/java/br/com/classholder/classholder/reservation/service/ReservationServiceDayRulesTest.java | Alterado | 0 |
| src/test/java/br/com/classholder/classholder/ClassholderApplicationTests.java | Alterado | 1 |
| src/test/java/br/com/classholder/classholder/reservation/repository/ReservationRepositoryIntegrationTest.java | Criado | 2 |
| src/test/java/br/com/classholder/classholder/reservation/controller/ReservationControllerIntegrationTest.java | Criado | 2 |
| src/test/java/br/com/classholder/classholder/reservation/controller/ReservationFlowIntegrationTest.java | Criado | 1 |
| src/test/resources/application-test.properties | Criado | 0 |

**Total de arquivos de teste:** 9  |  **Total de testes:** 22

## 6. Como executar os testes
```
./mvnw clean test
```

## 7. Evidências
- **Resultado da execução:** Tests run: 38, Failures: 0, Errors: 0, Skipped: 0 (Aqui consta 38, mas é porque está contando os testes que o projeto já possuia.)
- **Link do CI (se houver):** não se aplica

## 8. Decisões e dificuldades
- **O que foi mockado e por quê:** O que foi mockado e por quê:** Nos testes unitários, todas as dependências dos services foram substituídas por mocks do Mockito (repositórios, PasswordEncoder, TotpService, EmailService, AuditLogRepository, RoomService, UserService e HolidayService), para testar as regras de negócio isoladas, sem banco nem Spring. Nos testes de integração, nada foi mockado.
- **Bugs encontrados pelos testes (se houver):** nenhum
- **Dificuldades:** O sistema mostra páginas HTML (Thymeleaf) em vez de devolver JSON, então nos testes de integração conferimos a página e os dados exibidos. Para acessar as telas nos testes, foi preciso cadastrar um usuário de teste que já tivesse aceitado a LGPD e confirmado o 2FA, pois o sistema bloqueia quem não fez isso. Para os testes rodarem sem o banco PostgreSQL, foi criado um perfil de teste com banco H2 em memória. O total da seção 5 (22) conta os métodos de teste criados, a execução mostra 38 porque os testes parametrizados rodam uma vez para cada valor e a suíte também inclui testes antigos que não foram alterados.

## 9. Checklist de entrega
- [X] Todos os testes passam localmente com o comando da seção 6
- [X] Cada cenário listado nas seções 3 e 4 existe no código
- [X] Cada arquivo de teste alterado ou criado está listado na seção 5
- [X] Mínimos do exercício atendidos (10 unitários em 3 classes; 4 de integração)
- [X] Nenhum teste com @Disabled, sem asserção ou com Thread.sleep
- [ ] Professor adicionado como reviewer (Não adicionado como reviewer a pedido do próprio professor.)
