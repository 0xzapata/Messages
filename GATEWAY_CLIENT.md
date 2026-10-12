# Gateway client (fork of Fossify Messages)

The phone is a thin client. It posts each received SMS to the private phone gateway and does nothing else. No AI runs on the phone, and the gateway path never sends SMS. The contract is GATEWAY_CONTRACT.md, section "POST /v1/events (device)".

## What the client sends

- One `sms_received` event per received SMS: `id`, `kind`, `occurred_at`, `number`, `display_name`, `body`.
- `id` is a UUID v4. `occurred_at` is RFC 3339 UTC in seconds. `number` is the sender after `normalizePhoneNumber()`. `display_name` is the contact name, or empty when no contact matches.
- Request: `POST <gateway URL>/v1/events` with `Authorization: Bearer <device token>` and `Content-Type: application/json`. Connect timeout 15 s, read timeout 30 s.
- The body is cut at 3000 characters, because the event travels in WorkManager `Data` (10 KB limit).

## When

- At the end of `SmsReceiver.handleMessageSync`, after the message is stored, when the gateway is enabled and the sender passes the allowlist. An empty allowlist sends every event.
- The receiver enqueues one `GatewayWorker` and waits until WorkManager has persisted it. The worker runs when the network is connected.
- Settings, Gateway: enabled switch, gateway URL (`https://` only, else the app does not save), device token, allowlist, Save, and "Send test event".

## Retries

- Before it posts, the worker reads the settings again. If the gateway is off, or the number is no longer on the allowlist, it drops the event and does not post.
- The `id` is made at enqueue time and stored in the work input, so each retry sends the same `id`. The gateway returns `200` for a repeat and keeps one record.
- `201` or `200`: done. `400` or `401`: stop, and show one notification with the status code.
- Any other status, an I/O error, or a timeout: retry with exponential backoff from 30 s. After 10 retries, stop and show one notification.
- Notifications and logs never hold the body, the number, or the token.
