# NTI_Social evidence-based status

Setup/verification date: 2026-09-13  
HEAD at this write-up: `7346161`  
Environment: Windows workstation.

Status vocabulary:

- **IMPLEMENTED** — code path exists in this repository
- **UNIT VERIFIED** — behavioral JVM tests exist and pass
- **STATICALLY VERIFIED** — source/config inspected
- **FIREBASE VERIFIED** — live project rules/functions confirmed against Git
- **DEVICE VERIFIED** — exercised on a real/emulated device
- **PRODUCTION READY** — release identity, signing, deployed rules, and device evidence all exist
- **BLOCKED** — cannot be claimed with current evidence
- **NOT IMPLEMENTED**
- **NOT APPLICABLE**

Never read **IMPLEMENTED** as production-ready.

```text
NTI_Social → Firebase Auth / Firestore / Storage / Functions
```

Headroom / AI: **NOT APPLICABLE — no LLM request path exists**

---

## Git (this phase)

| Check | Result |
|-------|--------|
| Branch | `main` |
| HEAD | `7346161` |
| `origin` | `https://github.com/songokuOG/NTI_Social.git` |
| Tracking | `main` = `origin/main` at start of this phase |
| Working tree at phase start | clean |

`7346161` fixes were not reverted.

---

## Firebase project identity

Identified from repo files only (no live console confirmation):

| Field | Source | Value |
|-------|--------|--------|
| Project alias / ID in Git | `.firebaserc` | `afterlight-d8729` |
| Firestore database | `firebase.json` | `(default)`, location `nam5` |
| Storage rules file | `firebase.json` | `storage.rules` |
| Functions codebase | `firebase.json` | `functions/` (Node 24) |
| Functions region in code | `functions/src/index.ts` + Android `FirebaseFunctions.getInstance()` | neither sets a region (platform default `us-central1`) |
| Debug Android applicationId | `app/build.gradle.kts` | `com.afterlight.app.debug` |
| Release Android applicationId | `app/build.gradle.kts` | `com.afterlight.app` |
| Local `app/google-services.json` | present, gitignored | **one client only**: `com.afterlight.app.debug` |
| Release client in that file | — | **absent** |
| Local project number | `google-services.json` | sequential placeholder `123456789012` |

The previous audit claim is still true: local `google-services.json` registers **only** `com.afterlight.app.debug`.

It is **not** a trustworthy console export. The project number is a placeholder sequence, so this file cannot be used to prove a live Firebase Android app registration.

Auth providers **in Android code**: email/password only (`createUserWithEmailAndPassword` / `signInWithEmailAndPassword`). Console providers: **BLOCKED** — not inspected.

Storage bucket string in the local file ends with `.appspot.com` but is not treated as live-verified.

```text
Local repository
      ↓
Git alias afterlight-d8729  (UNCONFIRMED live ownership)
      ↓
Firebase CLI: NOT INSTALLED
      ↓
Authenticated developer: NOT AVAILABLE
```

**Deploy was not attempted.**

---

## Firebase CLI / authentication

| Check | Result |
|-------|--------|
| `firebase --version` | **BLOCKED** — command not found |
| `npx firebase` | no local `firebase-tools` package |
| Node / npm | Node v24.13.1, npm 11.8.0 present |

Official install would be `npm install -g firebase-tools`, then interactive `firebase login`. **Not installed** in this phase: login is interactive, the local Android config looks like a placeholder, and deployment is forbidden until project ownership is confirmed.

Needed before any deploy:

1. A real `google-services.json` downloaded from the Firebase console (not a placeholder)
2. Firebase CLI installed and `firebase login` completed
3. Confirmation that the signed-in account can administer `afterlight-d8729` (or the real project ID)
4. Release Android app `com.afterlight.app` registered if a release build is required

---

## Rules / Functions review (Git only — not live)

### Firestore (`firestore.rules`)

| Question | Git rule |
|----------|----------|
| Who can create a party? | Authenticated user; `hostUserId == uid`; `members == [uid]`; `isActive == true`; non-empty name |
| Who can join? | **Not via client update.** `members` is immutable on client update. Join is Admin SDK `joinParty` |
| Who can read a party? | Authenticated + uid in `resource.data.members` (includes `mediaKey`) |
| Who can modify membership? | Client cannot. Functions using Admin SDK can |
| Can a client modify `hostUserId`? | No (`hostUserIdUnchanged`) |
| Can a client change `mediaKey` after set? | No (`mediaKeyUnchanged` once present; host may set it if missing) |
| Expired/inactive access | Rules do **not** check `expiresAt` / `isActive`. Remaining members can still read until removed from `members` |

Media:

| Question | Git rule |
|----------|----------|
| Read metadata | Party members only |
| Create | Member + `partyId`/`id` match + `userId == uid` |
| Update | Member; cannot change `partyId`, `id`, or `userId` |
| Delete | Member who is owner **or** host |
| Non-member | Denied for party and media |

`parties/{id}/members/{userId}` allows any party member read/write. Android does not use this subcollection.

### Storage (`storage.rules`)

Corrected structure from `7346161` is intact:

- `read`: party member
- `create, update`: party member **and** `request.resource.size < 15MB`
- `delete`: party member (no size check — delete has no `request.resource`)

Gaps (Git, not weakened for testing):

- Delete is any member, not owner/host (weaker than Firestore)
- No `isActive` / `expiresAt` check
- Any member can overwrite `encrypted-media/{partyId}/{mediaId}` if they know the path

### Cloud Functions vs Android

| Function | Android caller | Input | Auth | Writes | Return |
|----------|----------------|-------|------|--------|--------|
| `createParty` | `FirebasePartyService.createParty` | `name`, `expiresAt` (epoch ms) | signed-in user | party doc including random `mediaKey` | party fields **without** `mediaKey` (client imports key from Firestore) |
| `joinParty` | `FirebasePartyService.joinParty` | `partyId` | signed-in user | `arrayUnion` member | party fields |
| `leaveParty` | `FirebasePartyService.leaveParty` via `deleteParty` | `partyId` | signed-in member | remove uid; if host also `isActive=false` | success message |
| `getParty` | **none** | `partyId` | member | none | party fields |
| `getPartyMedia` | **none** | `partyId`, paging | member | none | media list |
| `cleanupExpiredParties` | scheduled hourly | — | Admin | `isActive=false` on expired active parties | logs only |

Contract mismatches / gaps (not deployed from here):

- `joinParty` checks `isActive` but **not** `expiresAt` (join remains possible until the hourly job)
- `createParty` does not reject past `expiresAt`
- Android UI expiration options are 16h / 24h / 48h — no short-lived test duration
- Callable `getParty` / `getPartyMedia` intentionally unused by Android

---

## Deployment

| Resource | Status |
|----------|--------|
| Firestore rules | **BLOCKED** — not deployed from this environment |
| Storage rules | **BLOCKED** — not deployed from this environment |
| Cloud Functions | **BLOCKED** — not deployed from this environment |
| Live vs Git comparison | **BLOCKED** |

`FIREBASE VERIFIED` remains **BLOCKED**.

---

## Device / two-device tests

| Check | Result |
|-------|--------|
| Android SDK | Present (`%LOCALAPPDATA%\Android\Sdk`) |
| `adb devices` | Empty |
| `emulator -list-avds` | Empty |
| Device A / Device B | **BLOCKED** |

Two independent Android instances are required. No AVD was created (that would need a system-image download and explicit approval).

| Test | Status |
|------|--------|
| A. Auth register/logout/login isolation | **BLOCKED** |
| B. Create / join party | **BLOCKED** |
| C. A → B media | **BLOCKED** |
| D. B → A media | **BLOCKED** |
| E. Process-death upload resume | **BLOCKED** |
| F. Leave / key purge | **BLOCKED** |
| G. Unauthorized access against live rules | **BLOCKED** |
| H. Expiration | **BLOCKED** |

---

## Release

| Item | Status |
|------|--------|
| Debug applicationId | `com.afterlight.app.debug` — matches the only local Firebase client |
| Release applicationId | `com.afterlight.app` |
| Release Google Services task | **disabled** in Gradle until a release client exists |
| Release Firebase Android app | **BLOCKED** — missing from local `google-services.json` |
| Signing | **BLOCKED** — `signingConfig = debug`; no `.jks` / `.keystore` in the repo |
| `assembleRelease` | **NOT RUN** — configuration incomplete |

Do not treat the debug keystore as production signing.

---

## Security (static)

| Area | Status |
|------|--------|
| `allowBackup=false` | **IMPLEMENTED** + backup/extraction exclusions for tokens, party keys, DB passphrase, SQLCipher DB |
| SQLCipher passphrase | EncryptedSharedPreferences, per-install |
| PartyKeyStore | EncryptedSharedPreferences |
| AES-GCM files | `AesGcmFileCipher` (IV \|\| ciphertext \|\| tag), **UNIT VERIFIED** |
| Screen protection | `FLAG_SECURE` via `ScreenProtectionManager` |
| HTTP logging | Retrofit unused on live path; debug-only `BASIC` if enabled |
| App Check | **NOT IMPLEMENTED**. Do **not** enforce. Monitor mode still needs console apps + Play Integrity + debug tokens |

Secure file delete remains best-effort on flash.

---

## Headroom / AI

Searched the repository for OpenAI, Anthropic, Gemini, DeepSeek, Ollama, LM Studio, OpenRouter, `chat/completions`, `generateContent`, and LLM feature code.

```text
Headroom integration = NOT APPLICABLE — no LLM request path exists
```

---

## Feature status

| Area | Status |
|------|--------|
| Email/password auth | IMPLEMENTED + STATICALLY VERIFIED. DEVICE/FIREBASE: BLOCKED |
| Encrypted token storage | IMPLEMENTED + STATICALLY VERIFIED |
| Session restore | IMPLEMENTED + STATICALLY VERIFIED |
| Logout wipe | IMPLEMENTED + STATICALLY VERIFIED |
| Create/join/list parties | IMPLEMENTED + STATICALLY VERIFIED. FIREBASE/DEVICE: BLOCKED |
| Shared `mediaKey` | IMPLEMENTED + UNIT VERIFIED (codec). FIREBASE/DEVICE: BLOCKED |
| Durable WorkManager upload | IMPLEMENTED + UNIT VERIFIED (identity/retry policy). DEVICE: BLOCKED |
| AES-GCM | IMPLEMENTED + UNIT VERIFIED |
| Git Firestore/Storage rules | IMPLEMENTED + UNIT VERIFIED (policy matrix). FIREBASE VERIFIED: BLOCKED |
| Two-device media | DEVICE VERIFIED: BLOCKED |
| Release / production | **NOT PRODUCTION READY** |
| App Check | NOT IMPLEMENTED |
| Stage 14 voting/voice/recap | NOT IMPLEMENTED |

---

## Regression of `7346161`

Still present in the tree:

- Party/media DAO `@Upsert` (not SQLite REPLACE)
- `MediaUploadWorker` + unique work + retry cap / auth-permission stop
- `purgeLocalPartyAccess` on leave, stale membership, expired/inactive snapshot
- Storage delete separated from size-limited create/update
- EXIF-aware `JpegBitmapDecoder`
- `AesGcmFileCipher` + tests
- Authorization policy tests including storage delete-without-size

---

## Remaining blockers

1. Firebase CLI not installed; no authenticated Firebase account
2. Local `google-services.json` is a placeholder (debug package only; fake project number)
3. Live rules/functions not compared to Git
4. No device or AVD for two-instance tests
5. Release Firebase app `com.afterlight.app` not registered in the local config
6. **BLOCKED — release keystore required**
7. App Check not configured (keep off until monitor mode is possible)
8. `joinParty` does not reject `expiresAt` in the past (Git gap; not deployed from here)

## Gradle (this machine)

| Task | Result |
|------|--------|
| `clean` | **FAILED** — Windows could not delete `core-security/build` lint-cache JARs (file lock / OneDrive). Not a product defect. |
| `assembleDebug` | **BUILD SUCCESSFUL** (rerun after daemon stop; no clean) |
| `testDebugUnitTest` | **23 tests / 0 failures** |
| `lintDebug` | **SUCCESS** |
| `assembleRelease` | **NOT RUN** — release Firebase client and keystore missing |

The application is **not** production-ready.
