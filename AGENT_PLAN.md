# Agent bridge plan (fork of Fossify Messages)

Goal: keep this app as the default SMS app, and add a Claude agent hook for automations.
Distribution: sideload only. Not for Google Play.

## Verified insertion points (upstream main, 2026-10-10)

- Send choke point: `Context.sendMessageCompat` in `app/src/main/kotlin/org/fossify/messages/messaging/Messaging.kt`. All 4 callers route through it.
- Receive hook: end of `SmsReceiver.handleMessageSync` in `app/src/main/kotlin/org/fossify/messages/receivers/SmsReceiver.kt`, after the database insert.
- MMS hook: `MmsReceiver.handleMmsMessage`.
- The app has no INTERNET permission and no HTTP client.

## Changes

1. Add `android.permission.INTERNET` to `AndroidManifest.xml`.
2. Add one file `app/src/main/kotlin/org/fossify/messages/agent/AgentBridge.kt`.
   - On each received SMS from an allowlisted contact, post the event to the Claude API.
   - Model `claude-opus-5-5`, effort `low`, prompt caching on the system prompt.
   - Tools: `send_sms` (calls `sendMessageCompat`), `read_thread`, `lookup_contact`.
   - HTTP via `HttpURLConnection`. No new dependency.
   - Runs inside the existing `ensureBackgroundThread` block.
3. Skill workflow: the bridge reads `/sdcard/agent/skills/*.md`. One file is one automation. All files go into the cached system prompt. Edit automations as text, no rebuild.
4. Settings screen: API key, model, per-contact allowlist. The agent acts only on allowlisted contacts.

## Build and sideload

- Build on the devbox: `./gradlew assembleFossDebug`.
- Install from a Tailscale Serve URL on the phone, or `adb install` over wireless debugging.

## Out of scope

- Call events live in the Fossify Phone fork, same bridge shape.
- No call audio or transcription. Android blocks it for third-party apps.
