# Agent bridge plan (fork of Fossify Messages)

Goal: keep this app as the default SMS app, and add a Claude agent hook for automations.
Distribution: sideload only. Not for Google Play.

## Verified facts (2026-10-12)

- Send choke point: `Context.sendMessageCompat` in `app/src/main/kotlin/org/fossify/messages/messaging/Messaging.kt`. All 4 callers route through it.
- Receive hook: end of `SmsReceiver.handleMessageSync`, after the database insert.
- The SMS receiver has a 10 second limit. The process can die after `onReceive` returns. The Claude call takes 2 to 8 seconds on a good network, more on mobile data. So the receiver must not call the network.
- Android 11+ blocks direct paths on shared storage. A `/sdcard/agent/skills` folder does not work. The app must use the system folder picker.
- The API endpoint works. Tested through the router with model `claude-opus-5-5`, effort `low`, tool use, strict schemas, and prompt caching. Cache reads pass through.
- Router traps: tool names come back with an `_ide` suffix, and a non-streamed response ends with a `data: [DONE]` trailer. Strip the suffix. Parse the first JSON object only.
- Jev (TypeSafe AI) works as an injection gate. Endpoint `POST https://api.typesafe.ai/v1/systemone`. A test injection returned verdict `injection`, block 0.91, in 0.3 seconds.

## Data shape

One `AgentEvent`: kind (`sms_received`), address, body, threadId, subId, timestamp.
One `AgentPolicy`: allowlist of addresses, max sends per event (1), max sends per day, Jev block threshold.
One `AgentConfig`: Claude base URL, Claude key, model, effort, Jev key, skills folder URI.

## Changes

1. Manifest: add `android.permission.INTERNET`. Add a backup rule that excludes the agent preferences file.
2. Dependency: `androidx.work:work-runtime-ktx` 2.12.0. One new dependency, needed for the expedited job.
3. `SmsReceiver.handleMessageSync`: if the sender is on the allowlist, enqueue one expedited `AgentWorker` with the event as input data. Policy `RUN_AS_NON_EXPEDITED_WORK_REQUEST`. Unique work name per message id, policy KEEP, so a retry does not run twice.
4. `app/src/main/kotlin/org/fossify/messages/agent/AgentWorker.kt`:
   - Step 1, Jev gate. Post the body to Jev. If verdict is not `safe` or block is above the threshold, stop and write a notification.
   - Step 2, Claude call. System prompt = skills text, cached. Tools: `send_sms`, `read_thread`, `lookup_contact`. Tool loop with a cap of 4 rounds.
   - Step 3, tool execution. `send_sms` calls `sendMessageCompat`. The worker enforces the policy in code: recipient on the allowlist, 1 send per event, daily cap.
   - HTTP via `HttpURLConnection`. JSON via `org.json`. No HTTP dependency.
5. Skills: the user picks a folder once with `ACTION_OPEN_DOCUMENT_TREE`. The app persists the URI permission. The worker reads all `*.md` files in that folder with `DocumentFile`. One file is one automation. Edit automations as text, no rebuild.
6. Settings screen: section "AI agent" with base URL, Claude key, model, Jev key, skills folder, allowlist, daily cap.

## Build and sideload

- Build on the devbox: `./gradlew assembleFossDebug`.
- Install from a Tailscale Serve URL on the phone, or `adb install` over wireless debugging.

## Out of scope

- Call events live in the Fossify Phone fork, same worker shape.
- No call audio or transcription. Android blocks it for third-party apps.
