# notification-service

Sends the patient a **confirmation e-mail** for every appointment reminder. It is a **consumer**:
no business API, no GraphQL, no JWT. It listens on a RabbitMQ queue, looks up the patient's contact
from `identity-service`, and delivers the e-mail through a pluggable e-mail provider.

> **Delivery is currently simulated** (`LoggingEmailSender` just logs the e-mail instead of calling
> a real provider) — a real provider integration (Brevo) is a planned follow-up, not wired in yet.
> Everything else (queue, dedup, DLQ, patient lookup) is real.

Port **8082** (only `/actuator/health`). Database: `notification_db` (MySQL) — a single
idempotency table.

> Part of the [Hospital Appointment System](../README.md) — see the root README for the
> system-wide architecture, business rules, and how to run all four services together.

## Key features

- **`@RabbitListener`** consumer on `reminder.queue` — no inbound API of its own.
- Resolves the patient's **current** name and e-mail from `identity-service` on demand (cached with
  Caffeine), instead of storing a copy that could go stale.
- **Idempotent**: a redelivered reminder (`appointmentId` already processed) is a no-op.
- **Retry + dead-letter queue**: 3 retries on failure, then the message lands in `reminder.dlq`
  instead of being lost or retried forever.
- Delivery is a **pluggable port** (`EmailSender`) — currently a logging adapter that simulates
  sending; swapping in a real provider (e.g. Brevo) requires no change to the domain or use case.

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
   │   4. EmailSender.send(email)                     ── logs "Simulated e-mail..."│
   │   5. ProcessedReminderStore.markProcessed(appointmentId)  ─▶ notification_db  │
   │                                                                              │
   │  any exception ▶ Rabbit retries 3× ▶ message lands in "reminder.dlq"         │
   └──────────────────────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
                    container log: "Simulated e-mail sent to ..." (no real provider yet)
```

The reminder message carries **ids only**, so the service must call `identity-service` to get the
patient's name and e-mail. The result is cached (Caffeine) so a burst of reminders for the same
patient does not hammer `identity`.

---

## 2. Clean Architecture — what is pluggable

`domain` and `application` know **nothing** about RabbitMQ, e-mail providers, `RestClient` or
MySQL. Every external collaborator is a **port** (interface) defined in the core; infrastructure
supplies an adapter.

| Concern | Port (`application/port`) | Current adapter | Swap for — no core change |
|---|---|---|---|
| Send e-mail | `EmailSender` | `LoggingEmailSender` (logs, simulates success) | `BrevoEmailSender`, `ResendEmailSender`, `SmtpEmailSender` (`JavaMailSender`) — the real-provider follow-up |
| Patient contact | `UserDirectory` | `IdentityHttpUserDirectory` (`RestClient` + cache) | gRPC client, different HTTP client, test stub |
| Idempotency | `ProcessedReminderStore` | `JdbcProcessedReminderStore` (MySQL) | Redis, in-memory |
| Inbound transport | *(adapter, not a port)* | `ReminderListener` (`@RabbitListener`) | Kafka / SQS / HTTP endpoint — new listener, same use case |

**Example — plug in a real e-mail provider (the planned next step).** `EmailMessage` is
provider-neutral (`toEmail`, `toName`, `subject`, `htmlBody`). To wire up Brevo (or any other):

1. add `BrevoEmailSender implements EmailSender` under `infrastructure/email/` (maps `EmailMessage`
   to the provider's request body, reads its own `@ConfigurationProperties` for the API key/URL);
2. remove (or un-`@Component`) `LoggingEmailSender` — `SendAppointmentReminderUseCase` takes
   whichever single `EmailSender` bean Spring finds, autowired by type, no explicit `@Bean` needed.

`SendAppointmentReminderUseCase`, `EmailMessage`, and every test of the core stay untouched.

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
| a **registered patient** in `identity` (any e-mail — delivery is simulated) | the recipient the log line names | log in as the `identity` admin, `POST /users` with `role: PATIENT` |
| **`scheduling-service`** (+ its MySQL + RabbitMQ) | actually publishes reminders when an appointment is created | `cd ../scheduling-service && docker compose up` — **or** publish a test message by hand (§5) |

> `scheduling` and `notification` must talk to the **same** RabbitMQ. Either run one shared broker
> (point both at it) or, for a quick test, run only `notification`'s RabbitMQ and publish the
> reminder message manually (§5).

---

## 4. Configuration

No e-mail-provider credentials needed right now (delivery is simulated). Keys that matter
(`application.yaml`, all with sane defaults): `app.identity.base-url` (`http://localhost:8080`),
`app.reminder.rabbit.*` (matches `scheduling`), Rabbit listener retry (`max-attempts: 3`, then DLQ).

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
`identity`). It publishes a reminder and asserts: the reminder ends up in `processed_reminders`
(patient resolved, "e-mail" simulated); a duplicate message resolves the patient **only once**
(idempotency); when the identity lookup fails, the message ends up in `reminder.dlq`. It
**self-skips** when Docker is unavailable, so `./mvnw verify` still passes. JaCoCo line-coverage
gate: 80%.

### Manual — watch the simulated e-mail go out

1. Start the pieces:
   ```bash
   cd identity-service && docker compose up -d          # identity on 8080 (+ its MySQL)
   cd ../notification-service
   docker compose up --build                             # rabbitmq + mysql-notification + notification
   ```
2. In `identity`, register a patient (log in as admin, `POST /users`, `role: PATIENT`). Note its `id`.
3. Publish a reminder — pick one:
   - **Via `scheduling-service`:** start it (pointing at the same RabbitMQ), log in, `scheduleAppointment`
     with that `patientId`. It publishes the reminder automatically.
   - **By hand:** RabbitMQ UI (<http://localhost:15672>, guest/guest) → *Exchanges* →
     `appointment.reminders` → *Publish message*, routing key `appointment.reminder`, payload:
     ```json
     { "appointmentId": "test-1", "patientId": "<the patient id>", "scheduledAt": "2030-12-01T10:00:00" }
     ```
4. Watch `notification`'s log — `LoggingEmailSender` prints `Simulated e-mail sent to <name>
   <<email>> — subject: "..."`.
5. Re-publish the **same** `appointmentId` → no second log line (idempotent — already processed).
6. Publish with a `patientId` that doesn't exist in `identity` → after 3 attempts the message
   appears in `reminder.dlq` (RabbitMQ UI → *Queues*).

---

## 6. Resilience

- The consumer retries **3×** (2s initial back-off) on any exception, then rejects the message.
- `reminder.queue` dead-letters to **`reminder.dlq`** (declared by `scheduling`; also declared here
  so the service is self-sufficient).
- `default-requeue-rejected: false` → a rejected message goes to the DLQ, not back onto the queue.
- Idempotency (`processed_reminders`, key = `appointmentId`) makes a redelivered reminder a no-op.
