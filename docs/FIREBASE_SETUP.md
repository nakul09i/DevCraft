# Firebase Setup — DevCraft

Everything in code is done. This document covers the **console steps only you can do**.

Until `google-services.json` is added, DevCraft builds and runs as a fully offline
app: the Google Services Gradle plugin is skipped, `FirebaseApp` never initializes,
and the login screen is bypassed entirely. Nothing breaks.

---

## Project values

| Field | Value |
| :--- | :--- |
| Firebase project name | `DevCraft by Neutron` |
| Firebase project ID | `devcraft-by-neutron` |
| Billing plan | Spark (free) |
| Android `applicationId` | `com.neutron.devcraft` |
| Kotlin `namespace` | `com.devcraft` *(internal only — do NOT register this)* |
| Debug SHA-1 | `3C:C4:FF:A6:C8:F0:46:8F:11:F7:23:41:5D:CE:B4:28:D5:AC:1F:C9` |
| Debug SHA-256 | `3E:4A:EC:87:87:58:D4:3C:38:C6:F7:38:F7:B9:97:5D:5F:E3:E8:87:6A:F6:FB:92:63:CE:2D:60:AA:35:13:A8` |

> Register **`com.neutron.devcraft`**. The `namespace` differs on purpose — it keeps
> the 44 existing Kotlin files' package declarations untouched. Firebase and the
> Google Services plugin match on `applicationId`, which is the installed identity.

Those fingerprints come from the local debug keystore
(`%USERPROFILE%\.android\debug.keystore`). Certificate hashes are **not secrets** —
they are meant to be pasted into the console. A release build will have different
ones; see [Release signing](#release-signing).

---

## Steps

### 1. Register the Android app
Firebase console → project `devcraft-by-neutron` → **Add app → Android**.

- Android package name: `com.neutron.devcraft`
- App nickname: `DevCraft Android`
- Debug signing certificate SHA-1: the SHA-1 above

### 2. Add both fingerprints
Project settings → **Your apps** → the Android app → **Add fingerprint**.
Add the SHA-1 **and** the SHA-256 from the table.

Phone Auth uses SafetyNet/Play Integrity for silent verification. A missing or
wrong SHA-1 is the single most common cause of `verifyPhoneNumber` failing with
no SMS ever arriving.

### 3. Download `google-services.json`
Place it at exactly:

```
app/google-services.json
```

It is **gitignored** and must not be committed. Its presence is what activates
Firebase — `app/build.gradle.kts` applies the Google Services plugin conditionally:

```kotlin
if (project.file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
```

### 4. Enable Phone authentication
**Authentication → Sign-in method → Phone → Enable.**

### 5. Configure the SMS region policy
**Authentication → Settings → SMS region policy.** Allow **India (+91)**. Deny
everything else unless you need it — this is the main defence against SMS-pumping
abuse, which on Spark would simply exhaust your daily quota.

### 6. Add a test phone number (do this for the demo)
**Authentication → Sign-in method → Phone → Phone numbers for testing.**

Example: `+91 9999999999` with code `123456`.

**Use a test number for judging.** Test numbers:
- consume **no** SMS quota
- need no real SIM
- work on an emulator
- return the fixed code every time, so a live demo cannot fail on network

Spark plan sends a small number of real SMS per day. Real verification is fine to
demo once, but a test number is what you want on stage.

### 7. Firestore — not required yet
**Skip it.** DevCraft currently uses Firebase for **authentication only**. Room is
the local source of truth, and there is no sync transport implemented yet, so
creating a Firestore database now would add attack surface with nothing writing
to it.

When sync does land, the intended shape is:

```
users/{uid}/operations/{operationId}
```

and these rules — never `if true`:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/operations/{operationId} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

---

## What is a secret and what is not

| Value | Safe in the app? | Why |
| :--- | :--- | :--- |
| `google-services.json` | Yes, it ships inside the APK | Contains client identifiers only. Still gitignored here to keep project config out of a public repo. |
| Firebase API key | Yes | A project identifier, not an authorization token. Access is controlled by Auth + security rules. |
| Debug/release SHA fingerprints | Yes | Public certificate hashes. |
| **Service-account JSON** | **NO — never** | Full admin access, bypasses all rules. Server-side only. |
| **`MAPPLS_API_KEY`** | Via `local.properties` only | Gitignored; injected into `BuildConfig` at build time. |

`.gitignore` already blocks `google-services.json`, `service-account*.json`,
`*.jks`, `*.keystore`, `*.p12`, `*.pem`, `local.properties` and `secrets.properties`.

---

## Testing sign-in

1. `./gradlew assembleDebug`
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Launch. With `google-services.json` present you get the login screen.
4. Enter the test number, tap **Continue**.
5. Enter the test code, tap **Verify** → dashboard.

### Signing out
Dashboard → **Sign out** (shown only when Firebase is configured). This clears the
Firebase session and the offline-bypass flag, returning you to the login screen.

### Offline behaviour
- **Never signed in, no network:** login screen appears with
  *"Continue offline without signing in"*. Tap it and the full local workflow —
  ingest, parse, review, order, alerts, queries — works normally. The choice is
  persisted, so you are not asked again.
- **Signed in, then offline:** Firebase caches the session on disk. Reopening
  with no network keeps you authenticated; `currentUser()` is read locally and
  makes no request.
- **Sending an OTP offline:** fails with *"No connection. Sending a code needs
  network — you can continue offline instead."* This is inherent to SMS.
- **No `google-services.json` at all:** login is skipped entirely.

Authentication gates **multi-device sync only**. It never gates order creation.

---

## Release signing

Not configured. There is no keystore in this repo and none should be committed.
To produce a signed release build:

```powershell
keytool -genkeypair -v -keystore devcraft-release.jks `
  -keyalg RSA -keysize 2048 -validity 10000 -alias devcraft
```

Then add a `signingConfigs` block reading credentials from `local.properties`
(never inline), and register that keystore's SHA-1 and SHA-256 in Firebase too —
otherwise Phone Auth works in debug and silently fails in release.

---

## Troubleshooting

| Symptom | Cause |
| :--- | :--- |
| No SMS, no error | SHA-1 not registered, or Phone provider disabled |
| `INVALID_APP_CREDENTIAL` | SHA-1 mismatch between keystore and console |
| Works in debug, fails in release | Release keystore SHA not registered |
| `TOO_MANY_REQUESTS` | Spark daily SMS quota exhausted — use a test number |
| Login screen never appears | `google-services.json` missing, or you previously tapped "Continue offline" (clear it via **Sign in** on the dashboard) |
| `BILLING_NOT_ENABLED` | Some regions require Blaze for real SMS; test numbers still work on Spark |

---

## The one manual step

**Download `google-services.json` for `com.neutron.devcraft` (with both
fingerprints registered) and place it at `app/google-services.json`.**

Everything else — SDK wiring, phone/OTP flow, resend countdown, error handling,
session persistence, offline bypass, sign-out — is implemented and builds.
