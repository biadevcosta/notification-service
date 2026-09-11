# notification-service — como testar com Docker

Validado de ponta a ponta em 2026-09-11 (via o `docker-compose.yml` da raiz do repo, que sobe os
4 serviços juntos). Este arquivo é o roteiro pra testar **só** o notification.

> Entrega de e-mail é **simulada** (`LoggingEmailSender` só loga) — sem conta/API key de provedor
> nenhum necessária. Integração com um provedor real (Brevo) fica pra depois.

---

## 0. Pré-requisitos

- [ ] **Docker Desktop** aberto (o daemon rodando).
- [ ] **Temurin 21** + `JAVA_HOME` (ou o JDK embutido do VS Code).

---

## 1. Bateria de testes completa (com a integração)

```bash
cd notification-service
./mvnw verify
```

**Esperado:** `BUILD SUCCESS`. O `NotificationIntegrationTest` roda de verdade — sobe RabbitMQ +
MySQL em containers e stuba só o `identity` com WireMock:

| Cenário | Esperado |
|---|---|
| publica um `AppointmentReminderMessage` | o `appointmentId` aparece em `processed_reminders` (paciente resolvido, "e-mail" simulado no log) |
| publica o **mesmo** `appointmentId` 2× | `identity` é chamado **1 vez** (dedup) |
| `patientId` inexistente no `identity` | após o retry, a mensagem cai na `reminder.dlq` |

Roda também o gate JaCoCo (mín. 80% — hoje ~94%).

---

## 2. Subir o serviço

```bash
cd notification-service
docker compose up --build
```

Sobe: **`rabbitmq`** (5672 + UI 15672, guest/guest), **`mysql-notification`** (3307),
**`notification`** (8082).

> Alternativa dev: `docker compose up -d rabbitmq mysql-notification` e `./mvnw spring-boot:run`.
>
> Pra testar o **sistema inteiro** junto (recomendado), use o `docker compose up --build -d` da
> raiz do repo em vez deste — ele já sobe um Rabbit/Kafka compartilhado sem conflito de porta.

---

## 3. O que mais precisa estar rodando

| Precisa | Por quê | Como |
|---|---|---|
| **`identity-service`** (+ MySQL) | o evento só tem IDs; o notification chama `GET /users/{id}` pra pegar **nome + e-mail** | `cd ../identity-service && docker compose up -d` |
| um **paciente cadastrado** no `identity` | é o destinatário que aparece no log | logar como admin (`admin@hospital.local` / `admin12345`), `POST /users` com `role: PATIENT`; anotar o `id` |
| **`scheduling-service`** (+ MySQL + RabbitMQ) | produz os reminders quando um agendamento é criado | `cd ../scheduling-service && docker compose up -d` — **ou** publicar na mão (passo 4) |

> `scheduling` e `notification` precisam falar com o **mesmo** RabbitMQ.

---

## 4. Teste manual — ver o e-mail simulado

1. Suba `identity` + `notification`.
2. No `identity`, cadastre um paciente. Anote o `id` (ex.: `pat-abc`).
3. Publique um reminder — escolha um:
   - **Via `scheduling`:** suba ele apontando pro mesmo RabbitMQ, logue, `scheduleAppointment` com esse `patientId`.
   - **Na mão:** RabbitMQ UI (<http://localhost:15672>, guest/guest) → *Exchanges* → `appointment.reminders`
     → *Publish message*, routing key `appointment.reminder`, payload:
     ```json
     { "appointmentId": "test-1", "patientId": "pat-abc", "scheduledAt": "2030-12-01T10:00:00" }
     ```
4. Veja o log do `notification`: `Simulated e-mail sent to <nome> <<e-mail>> — subject: "..."`.
5. Publique o **mesmo** `appointmentId` de novo → não sai segunda linha de log (dedup).
6. Publique com um `patientId` que **não existe** no `identity` → após o retry, a mensagem
   aparece na `reminder.dlq` (RabbitMQ UI → *Queues*).

---

## 5. Problemas comuns

| Sintoma | Causa / solução |
|---|---|
| `./mvnw verify` pula a integração | Docker Desktop não está aberto. |
| `PatientContactNotFoundException` no log | o `patientId` publicado não existe no `identity`, ou o `identity` não está no ar / `app.identity.base-url` errado. |
| Erro de conexão RabbitMQ/MySQL ao subir | espere os containers ficarem *healthy* (o compose já usa `depends_on: condition: service_healthy`). |
| Container `notification` não alcança o `identity` | confirme `app.identity.base-url` (ou `APP_IDENTITY_BASE_URL` no compose) e que o `identity` está no ar. |

---

## 6. Arquivos importantes

| Arquivo | O quê |
|---|---|
| `plano-desenvolvimento.md` | roteiro original (11 passos ✅) + §3 = as costuras da Clean Architecture. A decisão de simular o e-mail (2026-09-11) veio depois do plano original, que previa Brevo — ver `README.md` §2 pra como plugar um provedor real. |
| `README.md` | doc da solução: fluxo, o que é pluggável, o que sobe no Docker, dependências |
| `src/main/resources/application.yaml` | config (8082, datasource 3307, rabbit + retry, `app.identity.*`) |
| `docker-compose.yml` | rabbitmq + mysql-notification + app |
