# Gateway contract, pinned copy

Copy of phone-gateway CONTRACT.md version 1 (2026-10-12). The client builds against this version. The gateway repo owns the original.

# Phone gateway API contract

Version 1, pinned 2026-10-12. Both Android clients and the backend build against this file. A change here is a change to all three.

## Purpose

Phones post SMS and call events to the gateway. The gateway stores each event, classifies it with Jev (TypeSafe AI), and serves the events to integrations through a read API. No AI agent runs on the phone or in the gateway.

## Transport and reachability

- HTTPS only. The gateway binds `127.0.0.1` inside a rootless Podman pod. Tailscale Serve exposes it on the tailnet only.
- Phones join the tailnet with the Tailscale Android app. No public endpoint exists. Public exposure is a separate, explicit decision.
- Request bodies are JSON with `Content-Type: application/json`. Maximum body size 16 KB. Unknown fields are rejected.
- Responses carry `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. There is no CORS.

## Credentials

Two kinds. A credential of one kind can never do the work of the other kind.

| Kind | Who holds it | Can do |
|---|---|---|
| `device` | one phone app install | `POST /v1/events` only |
| `reader` | one integration | `GET /v1/events`, `GET /v1/events/{id}` only |

- Create with the CLI: `bun run cli credential add --kind device --name "pixel-messages"`. The CLI prints the token one time. The database stores only `SHA-256(token)`.
- Tokens are 32 random bytes, base64url, prefix `pgw_dev_` or `pgw_rd_`.
- Send as `Authorization: Bearer <token>`. A missing, malformed, revoked, or wrong-kind token returns `401` with the body `{"error":"unauthorized"}`. The gateway never says which.
- Revoke with `bun run cli credential revoke --id <id>`.

## Rate limits

Token bucket per credential. `device`: 60 requests per minute. `reader`: 600 per minute. Over the limit returns `429` with `Retry-After` seconds.

## POST /v1/events (device)

One event per request. `kind` selects the shape. Fields outside the shape are rejected with `400`.

SMS received:

```json
{
  "id": "1b4e28ba-2fa1-4d3e-9c6a-7f0a1c2d3e4f",
  "kind": "sms_received",
  "occurred_at": "2026-10-12T07:00:00Z",
  "number": "+4512345678",
  "display_name": "Alice",
  "body": "Your code is 123456"
}
```

Call started or ended:

```json
{
  "id": "2c5f39cb-3ab2-4e4f-8d7b-8a1b2c3d4e5f",
  "kind": "call_ended",
  "occurred_at": "2026-10-12T07:05:30Z",
  "number": "+4512345678",
  "display_name": "Alice",
  "direction": "incoming",
  "duration_s": 42,
  "missed": false
}
```

Rules:

- `id`: UUID v4 made on the phone when the event is created. The pair (device, id) is unique. A repeat POST with the same id returns `200` and the stored record id. The first POST returns `201`. This makes phone retries safe.
- `kind`: `sms_received`, `call_started`, `call_ended`.
- `occurred_at`: RFC 3339 UTC. Not more than 7 days in the past and not more than 5 minutes in the future.
- `number`: E.164 when known, else the raw dialed string. Maximum 32 characters. Empty string for a private number.
- `display_name`: maximum 120 characters. Empty string when unknown.
- `body`: SMS only. Maximum 8000 characters.
- `direction`, `duration_s`, `missed`: calls only. `call_started` has `duration_s` 0 and `missed` false. `missed` is true only for an incoming `call_ended` that never connected.

Response: `{"id":"<uuid>","status":"stored"}`.

## GET /v1/events (reader)

Query parameters, all optional:

| Name | Values |
|---|---|
| `kind` | `sms_received`, `call_started`, `call_ended` |
| `number` | exact E.164 match |
| `since`, `until` | RFC 3339, on `occurred_at` |
| `verdict` | `safe`, `injection`, `jailbreak`, `pending`, `failed` |
| `limit` | 1 to 200, default 50 |
| `cursor` | opaque, from the previous response |

Response:

```json
{
  "events": [
    {
      "id": "1b4e28ba-2fa1-4d3e-9c6a-7f0a1c2d3e4f",
      "device": "pixel-messages",
      "kind": "sms_received",
      "occurred_at": "2026-10-12T07:00:00Z",
      "received_at": "2026-10-12T07:00:02Z",
      "number": "+4512345678",
      "display_name": "Alice",
      "body": "Your code is 123456",
      "classification": {
        "status": "done",
        "model": "jev-1.13.0",
        "verdict": "safe",
        "block_score": 0.02,
        "category": "otp_code",
        "spam_score": null,
        "classified_at": "2026-10-12T07:00:03Z"
      }
    }
  ],
  "next_cursor": null
}
```

Events are newest first. Readers get plaintext fields. Every read is written to the audit log with the credential id, the path, the filters, and the row count.

## GET /v1/events/{id} (reader)

One event in the same shape. Unknown id returns `404`.

## GET /healthz

No auth. Returns `{"ok":true}` and nothing else.

## Classification

- The gateway stores the event first, then classifies. A Jev failure never loses an event.
- `status`: `pending` (stored, not yet classified), `done`, `failed` (Jev error after retries; the error text is stored without any event content).
- A sweep every 60 seconds retries `pending` and `failed` events up to 5 times.
- Jev model is pinned: `jev-1.13.0`. Endpoint `POST https://api.typesafe.ai/v1/systemone`, `Authorization: Bearer <JEV_API_KEY>`.
- SMS questions, one Jev call: `verdict` (choice: safe, injection, jailbreak), `block` (noul), `category` (choice: otp_code, transactional, personal, marketing, scam, other). State sent to Jev: `SMS from <number> (<display_name>): <body>`.
- Call questions, one Jev call: `spam` (noul: likely spam or scam caller), `category` (choice: personal, business, scam, other). State sent to Jev: `Call <kind> from <number> (<display_name>), <direction>, <duration_s> s, missed=<missed>`. For calls `verdict` is `safe` and `block_score` is null.
- Jev receives only the state text. It never receives credentials or ids.

## Storage

- SQLite through `bun:sqlite`, WAL mode, file mode 0600, inside a named Podman volume.
- Columns `number`, `display_name`, and `body` are encrypted with AES-256-GCM. A fresh 12 byte IV per value. Key: HKDF-SHA256 from `GATEWAY_DATA_KEY` (32 random bytes, base64) with info `enc`.
- `number_hmac` = HMAC-SHA256 with a second HKDF key (info `hmac`) over the normalized number. Equality filters use this column. No plaintext number is ever indexed.
- Audit log table: `at`, `credential_id`, `method`, `path`, `query` (filter names and values, no event content), `rows`, `status`.
- Retention: a sweep deletes events older than `GATEWAY_RETENTION_DAYS` (default 90). Audit rows are kept 365 days.

## Logging

Logs hold method, path, status, credential id, duration, and error class. Never a token, a number, a name, or a body.

## Environment

| Name | Meaning |
|---|---|
| `GATEWAY_DATA_KEY` | 32 random bytes, base64. Loss means the data is unreadable. |
| `JEV_API_KEY` | TypeSafe key. |
| `GATEWAY_DB_PATH` | SQLite file path. Default `./data/gateway.db`. |
| `GATEWAY_PORT` | Default `4200`. Bind is always `127.0.0.1`. |
| `GATEWAY_RETENTION_DAYS` | Default `90`. |

## Out of scope for version 1

Outbound webhooks, event streams, MMS, write access for readers, any AI agent.
