# notification-service — Plano de pair programming

Documento de trabalho para construir o `notification-service` **passo a passo**, com foco em
**Clean Architecture**: nenhuma classe de `domain`/`application` pode conhecer Brevo, RabbitMQ,
`RestClient`, MySQL ou qualquer detalhe de infra. Tudo isso fica atrás de **portas**.

> **Decisões tomadas (2026-09-06):**
> 1. **Consome RabbitMQ** `reminder.queue` (mantém o desenho do README — Kafka continua sendo do `history`).
> 2. **Envio de e-mail real** via **Brevo** (API REST, 300/dia grátis, sem domínio) — atrás da porta `EmailSender`.
> 3. **Idempotência com banco**: tabela `processed_reminders` (MySQL `notification_db`), chave = `appointmentId`.
> 4. Sem API pública, sem GraphQL, sem JWT — é um **consumidor**.

---

## 1. Combinado de trabalho

Igual ao do `identity-service` (`../identity-service/plano-desenvolvimento.md` §1): um passo pequeno
por vez, explico antes e depois, paro e espero "ok", `./mvnw test`/`verify` a cada bloco, código em
inglês / conversa em português, sem `commit`/`push` sem pedir.

**Regra de ouro (revisar sempre):** abra qualquer classe de `domain` ou `application` e olhe os
`import`. Se aparecer `org.springframework`, `RestClient`, `RabbitTemplate`, `com.mysql`,
`@RabbitListener`, `@Cacheable`, `com.fasterxml.jackson`, ou a string `api.brevo.com` → **vazou**.
Todo colaborador externo do caso de uso é uma **interface (porta)** definida no core.

---

## 2. O que é o `notification-service`

| | |
|---|---|
| Papel | Consumidor de lembretes. Ao receber um reminder, **resolve o contato do paciente** e **envia um e-mail**. |
| Porta HTTP | **8082** (só `/actuator/health`; sem API de negócio) |
| Entrada | RabbitMQ `reminder.queue` (payload: `AppointmentReminderMessage{appointmentId, patientId, scheduledAt}`, só IDs) |
| Saídas | HTTP → `identity` `GET /users/{id}` (nome/e-mail, com cache) · HTTP → Brevo (e-mail) |
| Banco | `notification_db` (MySQL) — só a tabela `processed_reminders` (idempotência) |
| Resiliência | retry 3× no consumidor → `reminder.dlq` |
| Pacote base | `com.biadevcosta.notification` |

Fluxo de uma mensagem:

```
reminder.queue ──▶ ReminderListener (@RabbitListener)
                     │  mapeia Message -> SendReminderCommand(appointmentId, patientId, scheduledAt)
                     ▼
                   SendAppointmentReminderUseCase.execute(command)
                     1. processedStore.isProcessed(appointmentId)?  -> sim: retorna (idempotente)
                     2. contact = userDirectory.findPatient(patientId)      [porta -> identity HTTP + cache]
                     3. email   = EmailMessage.appointmentReminder(contact, scheduledAt)   [domínio: formata]
                     4. emailSender.send(email)                              [porta -> Brevo HTTP]
                     5. processedStore.markProcessed(appointmentId, now)     [porta -> MySQL]
                   (exceção em qualquer passo -> Rabbit re-tenta 3x -> reminder.dlq)
```

---

## 3. Arquitetura limpa — as costuras (o que dá pra trocar sem tocar no core)

| Costura | Porta (em `application/port`) | Adapter atual (`infrastructure`) | Trocar para (sem mexer no core) | O que o core enxerga |
|---|---|---|---|---|
| **Envio de e-mail** | `EmailSender` | `BrevoEmailSender` (`RestClient` → `api.brevo.com/v3/smtp/email`) | `ResendEmailSender`, `SmtpEmailSender` (`JavaMailSender`), `SesEmailSender`, `LogEmailSender` (dev) | `send(EmailMessage)` — `EmailMessage` é **neutro** (`toEmail`, `toName`, `subject`, `htmlBody`); zero vocabulário do Brevo (`sender`/`htmlContent`/`api-key` só existem dentro do adapter) |
| **Contato do paciente** | `UserDirectory` | `IdentityHttpUserDirectory` (`RestClient` + `@Cacheable` Caffeine) | cliente gRPC, outro HTTP client, um cache diferente, stub em teste | `findPatient(patientId) -> PatientContact` |
| **Idempotência** | `ProcessedReminderStore` | `JdbcProcessedReminderStore` (Spring Data JDBC + MySQL) | Redis, `ConcurrentHashMap` (dev), outra tabela | `isProcessed(appointmentId)` · `markProcessed(appointmentId, now)` |
| **Transporte de entrada** | *(não é porta — é adapter de entrada)* | `ReminderListener` (`@RabbitListener`) | Kafka, SQS, um `POST /internal/reminders` — **novo listener, mesmo use case** | o use case recebe `SendReminderCommand` (primitivos), **nunca** um `Message` nem o record de fila |
| **Relógio** | parâmetro `java.time.Clock` no use case | bean `Clock.systemUTC()` | `Clock.fixed(...)` nos testes | `LocalDateTime.now(clock)` |
| **Configuração** | — | `@ConfigurationProperties`: `EmailProperties`, `IdentityClientProperties`, `ReminderRabbitProperties` | — | nada; config é 100% infra |

### O que fica em `domain` (Java puro)

- **`PatientContact`** — record `(id, name, email)`, com validação leve (e-mail não-vazio).
- **`EmailMessage`** — record `(toEmail, toName, subject, htmlBody)` + factory
  **`EmailMessage.appointmentReminder(PatientContact contact, LocalDateTime scheduledAt)`** que monta
  o **assunto e o corpo** do e-mail. Essa é a lógica de "conteúdo da notificação" — testável sem infra.
- **`exception/`** — `NotificationException` (base), `PatientContactNotFoundException`,
  `EmailDeliveryException` (o adapter traduz erro de transporte/Brevo pra cá; o core não vê `HttpClientErrorException`).

### O que fica em `application`

- **`port/`** — `UserDirectory`, `EmailSender`, `ProcessedReminderStore` (3 interfaces).
- **`command/`** — `SendReminderCommand(appointmentId, patientId, scheduledAt)`.
- **`usecase/`** — `SendAppointmentReminderUseCase` (orquestra os 5 passos; depende só das 3 portas + `Clock`).

### Anti-padrões que NÃO vamos cometer

| Vazamento | Correção |
|---|---|
| use case importar `AppointmentReminderMessage` (DTO da fila) | use case recebe `SendReminderCommand` com primitivos; o `ReminderListener` faz o mapeamento |
| `EmailMessage` ter campos `sender`/`htmlContent` (nomes do Brevo) | campos neutros; o mapeamento pro corpo do Brevo é feito no `BrevoEmailSender` |
| use case fazer `catch (RestClientException)` / `HttpClientErrorException` | o adapter converte pra `EmailDeliveryException` / `PatientContactNotFoundException`; o core só conhece exceções do domínio |
| `@Cacheable` na interface `UserDirectory` | anotação vai no método do adapter `IdentityHttpUserDirectory` |
| chave de idempotência = "message id do Rabbit" | chave = `appointmentId` (id de negócio, independe do transporte) |
| `SendAppointmentReminderUseCase` anotado com `@Component`/`@Service` | é POJO; ligado por `@Bean` em `infrastructure/config/UseCaseConfig` |

---

## 4. Estrutura alvo

```
notification-service/src/main/java/com/biadevcosta/notification/
├── domain/
│   ├── PatientContact.java
│   ├── EmailMessage.java                (+ factory appointmentReminder(...))
│   └── exception/                       NotificationException, PatientContactNotFoundException, EmailDeliveryException
├── application/
│   ├── port/                            UserDirectory, EmailSender, ProcessedReminderStore
│   ├── command/                         SendReminderCommand
│   └── usecase/                         SendAppointmentReminderUseCase
└── infrastructure/
    ├── messaging/                       AppointmentReminderMessage (record da fila), RabbitConfig, ReminderListener
    ├── client/                          IdentityHttpUserDirectory (RestClient + @Cacheable)
    ├── email/                           BrevoEmailSender (RestClient -> Brevo), BrevoRequest/BrevoResponse (records internos)
    ├── persistence/                     ProcessedReminderEntity, ProcessedReminderJdbcRepository, JdbcProcessedReminderStore
    └── config/                          CacheConfig (@EnableCaching), RestClientConfig, UseCaseConfig,
                                         EmailProperties, IdentityClientProperties, ReminderRabbitProperties

src/main/resources/
├── application.yaml
└── db/migration/V1__create_processed_reminders.sql
```

---

## 5. Contrato de mensagem e configuração

**Fila** (declarado pelo `scheduling`, ver `scheduling-service/.../rabbit/RabbitConfig`):
exchange `appointment.reminders` (Direct) · routing key `appointment.reminder` · fila `reminder.queue`
(durável, dead-letter → `reminder.dlq`). Payload JSON `AppointmentReminderMessage(appointmentId, patientId, scheduledAt)`.

> O `Jackson2JsonMessageConverter` do produtor põe o header `__TypeId__` com a FQN da classe do
> `scheduling`. No consumidor, o converter será configurado com `TypePrecedence.INFERRED` para
> desserializar pelo tipo do parâmetro do `@RabbitListener`, ignorando o header.

**`application.yaml`** (chaves principais):

| Chave | Valor | Nota |
|---|---|---|
| `server.port` | `8082` | |
| `spring.datasource.url` | `jdbc:mysql://localhost:3307/notification_db` | root/root; Flyway roda `V1` |
| `spring.rabbitmq.*` | `localhost:5672` guest/guest | |
| `spring.rabbitmq.listener.simple.retry` | `enabled, max-attempts: 3, initial-interval: 2s` | esgotou → DLQ |
| `spring.rabbitmq.listener.simple.default-requeue-rejected` | `false` | rejeitado vai pra DLQ, não volta pra fila |
| `app.reminder.rabbit.{exchange,queue,routing-key,dlq}` | `appointment.reminders` / `reminder.queue` / `appointment.reminder` / `reminder.dlq` | |
| `app.identity.base-url` | `http://localhost:8080` | |
| `app.identity.cache.{max-size,ttl}` | `1000` / `10m` | Caffeine |
| `app.email.api-url` | `https://api.brevo.com/v3/smtp/email` | |
| `app.email.api-key` | `${BREVO_API_KEY:}` | **variável de ambiente**, nunca no Git |
| `app.email.from-email` / `from-name` | `${BREVO_FROM_EMAIL:no-reply@hospital.local}` / `Hospital Appointments` | remetente verificado no Brevo |

---

## 6. Roteiro de passos

- [x] **0. Setup** — `pom.xml` reescrito (removeu `graphql`+test, `security`+test; adicionou `amqp`, `cache`, `caffeine`, `actuator`, `spring-rabbit-test`, Testcontainers `mysql`+`rabbitmq` via `testcontainers-bom:1.21.3`, `wiremock-standalone:3.13.2`, plugin JaCoCo gate 80%; testes → `spring-boot-starter-test`). `docker-compose.yml` (rabbitmq:3-management + mysql-notification:3307 + app, healthchecks). `Dockerfile` (multi-stage, 8082). `public.pem` removido. `NotificationApplicationTests` marcado `@Disabled`. Compila.
- [x] **1. Config + migration** — `application.yaml` (8082, datasource 3307, rabbit + retry/DLQ, cache Caffeine, `app.reminder.rabbit.*` / `app.identity.*` / `app.email.*`); `V1__create_processed_reminders.sql` (`appointment_id` PK, `processed_at`).
- [x] **2. Domínio** — `PatientContact(id,name,email)`, `EmailMessage(toEmail,toName,subject,htmlBody)` **neutro** + factory `appointmentReminder(...)` (copy pt-BR), `NotificationException`/`PatientContactNotFoundException`/`EmailDeliveryException`. `PatientContactTest` + `EmailMessageTest` (4 casos). 5 testes.
- [x] **3. Portas + command** — `UserDirectory.findPatient`, `EmailSender.send`, `ProcessedReminderStore.isProcessed/markProcessed`, `SendReminderCommand(appointmentId, patientId, scheduledAt)`.
- [x] **4. `SendAppointmentReminderUseCase`** + teste (4): dedup pula; ordem `isProcessed → findPatient → send → markProcessed`; falha em `findPatient` (`PatientContactNotFoundException`) ou `send` (`EmailDeliveryException`) propaga e **não marca** processado.
- [x] **5. Messaging** — `AppointmentReminderMessage` (record próprio), `RabbitConfig` em `config/` (queue+DLQ igual ao `scheduling`; `JacksonJsonMessageConverter("com.biadevcosta.*")` + `TypePrecedence.INFERRED` — Jackson 3, ignora o `__TypeId__`), `ReminderRabbitProperties`, `ReminderListener` (`@RabbitListener` → command). `ReminderListenerTest`. 10 testes.
- [x] **6. `IdentityHttpUserDirectory`** + `CacheConfig` (`@EnableCaching`) + `IdentityClientProperties` — `RestClient` (builder) + `@Cacheable("patient-contacts")`; `HttpClientErrorException.NotFound` → `PatientContactNotFoundException`. Teste `MockRestServiceServer` (2). `@ConfigurationPropertiesScan` no app.
- [x] **7. `BrevoEmailSender`** + `EmailProperties` — `RestClient` (header `api-key`) → Brevo; mapeia `EmailMessage` → `{sender,to,subject,htmlContent}`; `RestClientException` → `EmailDeliveryException`. Teste `MockRestServiceServer` (2): confere header + jsonPath do payload; 401 → exceção.
- [x] **8. Persistência** — `ProcessedReminderEntity implements Persistable` (`isNew()==true`, insert-only), `ProcessedReminderJdbcRepository`, `JdbcProcessedReminderStore` (`existsById` / `save`, engole `DataIntegrityViolationException`/`DbActionExecutionException`). Teste Mockito (3).
- [x] **9. Wiring** — `UseCaseConfig` (`@Bean` `SendAppointmentReminderUseCase` + `Clock.systemUTC()`). 17 testes.
- [x] **10. Integração** — `AbstractIntegrationTest` (MySQL + RabbitMQ Testcontainers, `assumeTrue` Docker, `@DynamicPropertySource`), `NotificationIntegrationTest` (`@SpringBootTest` NONE + WireMock stubando identity e Brevo, `@ActiveProfiles("test")` c/ retry rápido): (1) reminder → POST no Brevo com o e-mail resolvido; (2) reminder duplicado → 1 e-mail só (dedup); (3) Brevo 500 → mensagem na `reminder.dlq`. `NotificationApplicationTests` removido. `./mvnw verify` → 16 unit 0 fail, **JaCoCo 97.9%**.
- [x] **11. Fechamento** — `README.md` (solução: fluxo, costuras da Clean Architecture, o que sobe no Docker, serviços externos, como testar), `TESTAR-COM-DOCKER.md` (checklist), JaCoCo já no pom, `Dockerfile` (passo 0). Allure: pulado (opcional).

---

## 7. Notas do skeleton (passo 0)

Estado real após `git submodule update --init notification-service`:

- **Parent** `spring-boot-starter-parent:4.1.0`, `groupId=com.biadevcosta`, `artifactId=notification`, Java 21.
- `application.yaml`: `server.port: 8082`, datasource `jdbc:mysql://localhost:3307/notification_db` root/root
  (o `docker-compose.yml` mapeia `3307:3306`, só MySQL).
- pom com o mesmo scaffold genérico do identity: `data-jdbc`, `flyway`, `graphql`, `security`,
  `validation`, `webmvc`, `flyway-mysql`, `mysql-connector-j` + starters de teste por fatia.
- `src/main/resources/public.pem` presente — **não é necessário** (sem JWT), apagar.

### pom — o que muda

| Manter | Adicionar | Remover |
|---|---|---|
| `data-jdbc`, `flyway`, `flyway-mysql`, `mysql-connector-j`, `webmvc`, `validation` | `spring-boot-starter-amqp`, `spring-boot-starter-cache`, `com.github.ben-manes.caffeine:caffeine`, `spring-boot-starter-actuator` | `spring-boot-starter-graphql` (+ test), `spring-boot-starter-security` (+ test) |
| plugin `spring-boot-maven-plugin` | Testcontainers: `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, `:mysql`, `:rabbitmq` | 6 starters de teste "por fatia" → `spring-boot-starter-test` + `spring-rabbit-test` |
| | `org.wiremock:wiremock-standalone` (stub HTTP nos testes) | |
| | plugin **JaCoCo** 0.8.13 (gate 80%; excludes: `*Application`, `infrastructure/config/**`) | |

---

## 8. Dependências de runtime (para o serviço fazer algo de fato)

Ver o `README.md` para o passo a passo. Resumo do que precisa estar de pé:

| Precisa | Por quê |
|---|---|
| **RabbitMQ** | a fila de onde as mensagens vêm |
| **MySQL** `notification_db` | tabela de idempotência |
| **`identity-service`** (+ seu MySQL) | `GET /users/{id}` para o nome/e-mail do paciente |
| um **paciente cadastrado** no `identity` com e-mail real | destinatário do e-mail |
| **`scheduling-service`** (+ MySQL + RabbitMQ) | produz os reminders quando um agendamento é criado — *ou* publica-se uma mensagem de teste na exchange manualmente (RabbitMQ UI) |
| **conta Brevo** + `BREVO_API_KEY` + remetente verificado | envio real do e-mail |
