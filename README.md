# notification-service

Sends the patient a **confirmation e-mail** for every appointment reminder. It is a **consumer**:
no business API, no GraphQL, no JWT. It listens on a RabbitMQ queue, looks up the patient's contact
from `identity-service`, and delivers the e-mail through a pluggable e-mail provider.

Port **8082** (only `/actuator/health`). Database: `notification_db` (MySQL) — a single
idempotency table.

---

## 1. How it works (end to end)

```
                         (another service produces the reminder)
scheduling-service ──publishes──▶ RabbitMQ exchange "appointment.reminders"
                                     routing key "appointment.reminder"
                                          │
                                          ▼
                                  queue "reminder.queue"   (DLQ: "reminder.dlq")
                                  payload: AppointmentReminderMessage
                                           { appointmentId, patientId, scheduledAt }   (ids only)
                                          │
                                          ▼
   ┌───────────────────────────  notification-service  ───────────────────────────┐
   │  ReminderListener (@RabbitListener)                                           │
   │    maps the message ──▶ SendReminderCommand(appointmentId, patientId, when)   │
   │                                                                              │
   │  SendAppointmentReminderUseCase:                                             │
   │   1. processed?  ── yes ─▶ stop (idempotent; Kafka/Rabbit may redeliver)     │
   │   2. contact  = UserDirectory.findPatient(patientId)   ── HTTP ─▶ identity    │
   │                                                          GET /users/{id}      │
   │                                                          (name + email, cached)│
   │   3. email    = EmailMessage.appointmentReminder(contact, scheduledAt)        │
   │   4. EmailSender.send(email)                           ── HTTP ─▶ Brevo API    │
   │   5. ProcessedReminderStore.markProcessed(appointmentId)  ─▶ notification_db  │
   │                                                                              │
   │  any exception ▶ Rabbit retries 3× ▶ message lands in "reminder.dlq"         │
   └──────────────────────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
                              patient's inbox ✉  (real e-mail via Brevo)
```

The reminder message carries **ids only**, so the service must call `identity-service` to get the
patient's name and e-mail. The result is cached (Caffeine) so a burst of reminders for the same
patient does not hammer `identity`.

---

## 2. Clean Architecture — what is pluggable

`domain` and `application` know **nothing** about RabbitMQ, Brevo, `RestClient` or MySQL. Every
external collaborator is a **port** (interface) defined in the core; infrastructure supplies an adapter.

| Concern | Port (`application/port`) | Current adapter | Swap for — no core change |
|---|---|---|---|
| Send e-mail | `EmailSender` | `BrevoEmailSender` (HTTP → `api.brevo.com`) | `ResendEmailSender`, `SmtpEmailSender` (`JavaMailSender`), `LogEmailSender` |
| Patient contact | `UserDirectory` | `IdentityHttpUserDirectory` (`RestClient` + cache) | gRPC client, different HTTP client, test stub |
| Idempotency | `ProcessedReminderStore` | `JdbcProcessedReminderStore` (MySQL) | Redis, in-memory |
| Inbound transport | *(adapter, not a port)* | `ReminderListener` (`@RabbitListener`) | Kafka / SQS / HTTP endpoint — new listener, same use case |

**Example — replace the e-mail provider.** `EmailMessage` is provider-neutral
(`toEmail`, `toName`, `subject`, `htmlBody`). To move from Brevo to, say, Resend:

1. add `ResendEmailSender implements EmailSender` under `infrastructure/email/` (maps `EmailMessage`
   to Resend's request body, reads its own `@ConfigurationProperties`);
2. point the `@Bean EmailSender` in `infrastructure/config/UseCaseConfig` at the new class.

`SendAppointmentReminderUseCase`, `EmailMessage`, and every test of the core stay untouched.

Full seam analysis and the anti-patterns we avoid: `plano-desenvolvimento.md` §3.

---

## 3. What runs where

### In `docker-compose.yml` (this folder)

| Service | Port(s) | Role |
|---|---|---|
| `rabbitmq` | 5672, 15672 (UI) | the queue this service consumes |
| `mysql-notification` | 3307→3306 | `notification_db` (idempotency table) |
| `notification` | 8082 | the service itself |

### External — must also be running for a real e-mail to go out

| Dependency | Why | How |
|---|---|---|
| **`identity-service`** (+ its MySQL) | `GET /users/{id}` → the patient's **name and e-mail** | `cd ../identity-service && docker compose up` |
| a **registered patient** in `identity` with a real e-mail | the recipient | log in as the `identity` admin, `POST /users` with `role: PATIENT` and your e-mail |
| **`scheduling-service`** (+ its MySQL + RabbitMQ) | actually publishes reminders when an appointment is created | `cd ../scheduling-service && docker compose up` — **or** publish a test message by hand (§5) |
| a **Brevo account** | real e-mail delivery | see §4 |

> `scheduling` and `notification` must talk to the **same** RabbitMQ. Either run one shared broker
> (point both at it) or, for a quick test, run only `notification`'s RabbitMQ and publish the
> reminder message manually (§5).

---

## 4. Configuration

E-mail (Brevo) — the API key is **never committed**; it comes from the environment:

| Env var | Meaning |
|---|---|
| `BREVO_API_KEY` | Brevo → *SMTP & API* → **API key (v3)** |
| `BREVO_FROM_EMAIL` | a **verified sender** e-mail in your Brevo account |

Other keys (`application.yaml`, all with sane defaults): `app.identity.base-url`
(`http://localhost:8080`), `app.reminder.rabbit.*` (matches `scheduling`), Rabbit listener retry
(`max-attempts: 3`, then DLQ).

Get a Brevo key: create a free account → *Senders, Domains & Dedicated IPs* → add and verify your
sender e-mail → *SMTP & API* → *API Keys* → generate. Free tier: 300 e-mails/day.

---

## 5. How to run and test

### Unit tests (no Docker, no network)

```bash
cd notification-service
./mvnw test
```

Covers the domain (e-mail copy formatting), the use case (dedup, ordering, failure handling — ports
mocked), and each adapter (`MockRestServiceServer` for the HTTP ones).

### Integration test

```bash
./mvnw verify
```

`NotificationIntegrationTest` uses **Testcontainers** (RabbitMQ + MySQL) and **WireMock** (stubs
`identity` and Brevo). It publishes a reminder and asserts: WireMock received a Brevo call with the
patient's e-mail; a duplicate message triggers **one** Brevo call (idempotency); when Brevo returns
`500` the message ends up in `reminder.dlq`. It **self-skips** when Docker is unavailable, so
`./mvnw verify` still passes. JaCoCo line-coverage gate: 80%.

### Manual — see a real e-mail

1. Start the pieces:
   ```bash
   cd identity-service && docker compose up -d          # identity on 8080 (+ its MySQL)
   cd ../notification-service
   export BREVO_API_KEY=...   BREVO_FROM_EMAIL=you@verified.com
   docker compose up --build                             # rabbitmq + mysql-notification + notification
   ```
2. In `identity`, register a patient with **your** e-mail (log in as admin, `POST /users`,
   `role: PATIENT`). Note its `id`.
3. Publish a reminder — pick one:
   - **Via `scheduling-service`:** start it (pointing at the same RabbitMQ), log in, `scheduleAppointment`
     with that `patientId`. It publishes the reminder automatically.
   - **By hand:** RabbitMQ UI (<http://localhost:15672>, guest/guest) → *Exchanges* →
     `appointment.reminders` → *Publish message*, routing key `appointment.reminder`, payload:
     ```json
     { "appointmentId": "test-1", "patientId": "<the patient id>", "scheduledAt": "2030-12-01T10:00:00" }
     ```
4. Watch `notification`'s log (`reminder sent to <email>`), then check the inbox.
5. Re-publish the **same** `appointmentId` → the log says it was already processed, **no** second e-mail.
6. Set a bad `BREVO_API_KEY` and publish → after 3 attempts the message appears in `reminder.dlq`
   (RabbitMQ UI → *Queues*).

---

## 6. Resilience

- The consumer retries **3×** (2s initial back-off) on any exception, then rejects the message.
- `reminder.queue` dead-letters to **`reminder.dlq`** (declared by `scheduling`; also declared here
  so the service is self-sufficient).
- `default-requeue-rejected: false` → a rejected message goes to the DLQ, not back onto the queue.
- Idempotency (`processed_reminders`, key = `appointmentId`) makes a redelivered reminder a no-op.
