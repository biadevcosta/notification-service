# notification-service — como testar quando o Docker estiver instalado

Checklist pra retomar. O **código está pronto** (16 testes unit passando, cobertura 97.9%, jar
empacota). Falta o que precisa de Docker: o teste de integração (Testcontainers) e ver um e-mail
real chegar.

---

## 0. Pré-requisitos

- [ ] **Docker Desktop** instalado e **aberto** (o daemon rodando).
- [ ] **Temurin 21** + `JAVA_HOME` (ou usar o JDK embutido do VS Code — ver `../identity-service/TESTAR-COM-DOCKER.md` §0).
- [ ] **Conta Brevo** (grátis): criar conta → *Senders, Domains & Dedicated IPs* → **verificar um e-mail remetente** → *SMTP & API* → *API Keys* → **gerar uma API key v3**.
- [ ] Exportar as variáveis:
  ```bash
  export BREVO_API_KEY=xkeysib-....
  export BREVO_FROM_EMAIL=seu-remetente-verificado@gmail.com
  ```

---

## 1. Bateria de testes completa (com a integração)

```bash
cd C:/Users/Mikael/bia-workspace/hospital-system/notification-service
./mvnw verify
```

**Esperado:** `BUILD SUCCESS`. Agora o `NotificationIntegrationTest` **roda de verdade** — sobe
RabbitMQ + MySQL em containers e stuba `identity` e Brevo com WireMock:

| Cenário | Esperado |
|---|---|
| publica um `AppointmentReminderMessage` | WireMock recebe `POST /v3/smtp/email` com `$.to[0].email` = e-mail do paciente |
| publica o **mesmo** `appointmentId` 2× | Brevo é chamado **1 vez** (dedup via `processed_reminders`) |
| Brevo responde `500` | após o retry, a mensagem cai na `reminder.dlq` |

Roda também o gate JaCoCo (mín. 80% — hoje ~98%).

---

## 2. Subir o serviço

```bash
cd notification-service
docker compose up --build
```

Sobe: **`rabbitmq`** (5672 + UI 15672, guest/guest), **`mysql-notification`** (3307), **`notification`** (8082).
O compose passa `BREVO_API_KEY` / `BREVO_FROM_EMAIL` do ambiente para o container.

> Alternativa dev: `docker compose up -d rabbitmq mysql-notification` e `./mvnw spring-boot:run`.

---

## 3. O que mais precisa estar rodando (para um e-mail real sair)

| Precisa | Por quê | Como |
|---|---|---|
| **`identity-service`** (+ MySQL) | o evento só tem IDs; o notification chama `GET /users/{id}` pra pegar **nome + e-mail** | `cd ../identity-service && docker compose up -d` |
| um **paciente cadastrado** no `identity` com **e-mail real** | é o destinatário | logar como admin do identity (`admin@hospital.local` / `admin12345`), `POST /users` com `role: PATIENT` e o **seu** e-mail; anotar o `id` |
| **`scheduling-service`** (+ MySQL + RabbitMQ) | produz os reminders quando um agendamento é criado | `cd ../scheduling-service && docker compose up -d` — **ou** publicar na mão (passo 4) |

> `scheduling` e `notification` precisam falar com o **mesmo** RabbitMQ. Pro teste rápido, use só o
> RabbitMQ do `notification` e publique a mensagem manualmente.

---

## 4. Teste manual — ver o e-mail chegar

1. Suba `identity` + `notification` (com `BREVO_API_KEY`/`BREVO_FROM_EMAIL` setados).
2. No `identity`, cadastre um paciente com o **seu** e-mail. Anote o `id` (ex.: `pat-abc`).
3. Publique um reminder — escolha um:
   - **Via `scheduling`:** suba ele apontando pro mesmo RabbitMQ, logue, `scheduleAppointment` com esse `patientId`.
   - **Na mão:** RabbitMQ UI (<http://localhost:15672>, guest/guest) → *Exchanges* → `appointment.reminders`
     → *Publish message*, routing key `appointment.reminder`, payload:
     ```json
     { "appointmentId": "test-1", "patientId": "pat-abc", "scheduledAt": "2030-12-01T10:00:00" }
     ```
4. Veja o log do `notification` e **cheque a caixa de entrada**.
5. Publique o **mesmo** `appointmentId` de novo → não sai segundo e-mail (dedup).
6. Coloque um `BREVO_API_KEY` inválido e publique → após o retry, a mensagem aparece na
   `reminder.dlq` (RabbitMQ UI → *Queues*).

---

## 5. Problemas comuns

| Sintoma | Causa / solução |
|---|---|
| `./mvnw verify` pula a integração | Docker Desktop não está aberto. |
| App sobe mas e-mail não sai; log `Brevo rejected` | `BREVO_API_KEY` errada ou remetente não verificado no Brevo. |
| `PatientContactNotFoundException` no log | o `patientId` publicado não existe no `identity`, ou o `identity` não está no ar / `app.identity.base-url` errado. |
| Erro de conexão RabbitMQ/MySQL ao subir | espere os containers ficarem *healthy* (o compose já usa `depends_on: condition: service_healthy`). |
| Container `notification` não alcança o `identity` | `app.identity.base-url` no compose aponta pra `http://host.docker.internal:8080` — confirme que o `identity` está exposto na 8080 do host. |

---

## 6. Arquivos importantes

| Arquivo | O quê |
|---|---|
| `plano-desenvolvimento.md` | roteiro (11 passos ✅) + §3 = as costuras da Clean Architecture |
| `README.md` | doc da solução: fluxo, o que é pluggável, o que sobe no Docker, dependências |
| `src/main/resources/application.yaml` | config (8082, datasource 3307, rabbit + retry, `app.email.*`, `app.identity.*`) |
| `docker-compose.yml` | rabbitmq + mysql-notification + app |
