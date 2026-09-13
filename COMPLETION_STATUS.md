# NTI_Social evidence-based status

Audit date: 2026-09-13  
HEAD at audit start: `ab3f936`  
Review pass: Room upsert (avoid REPLACE CASCADE), Storage delete rules, expired-party local purge, upload retry limits.  
Environment: Windows workstation, no attached emulator/device, no Firebase CLI.

Status vocabulary used below:

- **IMPLEMENTED** — code path exists in this repository
- **UNIT TESTED** — behavioral JVM tests exist and pass
- **STATICALLY VERIFIED** — source/config inspected
- **FIREBASE VERIFIED** — live project rules/functions confirmed
- **DEVICE VERIFIED** — exercised on a real/emulated device
- **BLOCKED** — cannot be claimed with current evidence
- **NOT IMPLEMENTED**

Never read "IMPLEMENTED" as production-ready.

```text
NTI_Social → Firebase Auth / Firestore / Storage / Functions
```

No AI/LLM path exists. Headroom is not appropriate.

---

## Feature status

| Area | Status |
|------|--------|
| Email/password auth | IMPLEMENTED + STATICALLY VERIFIED. DEVICE VERIFIED: BLOCKED |
| Encrypted token storage | IMPLEMENTED + STATICALLY VERIFIED |
| Session restore via Firebase Auth | IMPLEMENTED + STATICALLY VERIFIED |
| Logout wipe of tokens, Room, keys, files | IMPLEMENTED + STATICALLY VERIFIED |
| Create/join/list parties | IMPLEMENTED + STATICALLY VERIFIED. FIREBASE/DEVICE: BLOCKED |
| Party listener + stale purge | IMPLEMENTED + STATICALLY VERIFIED |
| Party detail countdown | IMPLEMENTED + STATICALLY VERIFIED |
| Shared `mediaKey` | IMPLEMENTED + UNIT TESTED (codec) + STATICALLY VERIFIED. FIREBASE/DEVICE: BLOCKED |
| Remote media sync | IMPLEMENTED + UNIT TESTED (mapping/idempotency) + STATICALLY VERIFIED. DEVICE VERIFIED: BLOCKED |
| Camera JPEG capture | IMPLEMENTED + STATICALLY VERIFIED. DEVICE VERIFIED: BLOCKED |
| AES-GCM encrypt/decrypt | IMPLEMENTED + UNIT TESTED |
| Durable media upload | IMPLEMENTED (WorkManager) + UNIT TESTED (work identity). DEVICE VERIFIED: BLOCKED |
| Local gallery + EXIF decode | IMPLEMENTED + STATICALLY VERIFIED |
| Screen protection | IMPLEMENTED + STATICALLY VERIFIED |
| Party expiration (client worker + scheduled function) | IMPLEMENTED + STATICALLY VERIFIED. FIREBASE/DEVICE: BLOCKED |
| SQLCipher passphrase | IMPLEMENTED + STATICALLY VERIFIED |
| Firestore/Storage rules in Git | IMPLEMENTED + UNIT TESTED (policy matrix). FIREBASE VERIFIED: BLOCKED — not deployed from this environment |
| App Check | NOT IMPLEMENTED. Enforcement BLOCKED pending console + Play Integrity |
| Release Firebase client | BLOCKED — `google-services.json` has only `com.afterlight.app.debug` |
| Production signing | BLOCKED — release uses debug keystore |
| Two-device media round-trip | DEVICE VERIFIED: BLOCKED — `adb devices` empty, no AVDs listed |
| Stage 14 voting/voice/recap | NOT IMPLEMENTED (excluded modules) |

---

## Shared media key design (accepted with constraints)

The key is a random 32-byte value, Base64-encoded in the party document, readable only by current members via Firestore rules. Every member must have the same AES key to decrypt Storage objects.

This is **access-control-by-Firestore-membership**, plus local at-rest encryption.

Acceptable because:

- Non-members cannot read the party document if deployed rules match Git
- Host cannot rotate `mediaKey` after it exists
- Join/leave membership is intended to go through Admin SDK functions
- Device cache is now wiped on leave, stale membership, and logout

Residual risk: any **current** member who can read photos can also read `mediaKey`. That is inherent to shared-key encryption.

---

## Two-device expected behavior (not device-verified)

1. Device A creates a party. Host persists `mediaKey` (function if deployed, otherwise host client write).
2. Device A captures a JPEG, encrypts it, writes Room + `sync_state=PENDING`, enqueues unique WorkManager upload.
3. Upload writes Storage `encrypted-media/{partyId}/{mediaId}` then Firestore `parties/{partyId}/media/{mediaId}`.
4. Device B joins, imports `mediaKey`, party listener adds the party, media listener upserts existing and new documents.
5. Gallery decrypt downloads the `.enc` file if missing, then AES-GCM decrypts with the shared key.
6. If B leaves or is removed, local key, Room media, and party files are purged. Firestore/Storage access is denied once `members` no longer contains B.

---

## Authoritative expiration

Server fields `expiresAt` + `isActive` are authoritative. The client countdown uses device clock and can be wrong offline. The Firestore party listener soft-deletes when the server marks a party inactive/expired. The client worker is local cleanup, not the source of truth.

---

## App Check plan (not implemented)

Do **not** enable enforcement yet. It would break debug package / missing Play Integrity setup.

When ready:

1. Add Play Integrity (release) and Debug provider (debug)
2. Register debug tokens in the Firebase console
3. Enable App Check on Auth, Firestore, Storage, Functions in monitor mode
4. Only then enforce

---

## Intentionally kept

- `getParty` / `getPartyMedia` Cloud Functions — public callable contract; Android does not call them
- Retrofit / WebSocket / `CameraController` / Face+Sync entities — compiled but unused
- `feature-voting` / `feature-voice` / `feature-recap` — absent

---

## Remaining blockers

### Code

- No instrumented two-device test
- Upload WorkManager success is not shown in UI
- Secure delete is best-effort on flash

### Firebase deployment

- Rules and updated `createParty` (`mediaKey`) are **not** confirmed deployed
- No Firebase CLI in this environment

### Device verification

- No emulator/device attached

### Release

- Missing `com.afterlight.app` in `google-services.json`
- Debug signing for release
- App Check absent

### Security (if Git rules are not deployed)

- Live project may still be using older/test rules. Treat as HIGH until deployment is verified.
