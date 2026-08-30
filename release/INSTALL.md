# DevCraft — Install

## Artifact

| Field | Value |
| :--- | :--- |
| File | `DevCraft-Master-debug.apk` |
| SHA-256 | `5131CD816B5FB38DA583664E3392E665D78C6A19F729172657DFFA12AF46C262` |
| Package ID | `com.neutron.devcraft` |
| Version | 1.0 (versionCode 1) |

| Min Android | 8.0 Oreo (API 26) |
| Target | Android 14 (API 34) |
| Build type | **debug** — signed with the local debug keystore |

Verify before installing:

```powershell
Get-FileHash DevCraft-Master-debug.apk -Algorithm SHA256
# must match SHA256SUMS.txt
```

```bash
sha256sum -c SHA256SUMS.txt
```

## Install

```bash
adb install -r DevCraft-Master-debug.apk
```

`adb` lives at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.

Sideloading instead: copy the APK to the phone and open it. Android will ask you
to allow installs from that source. Because this is a debug build, Play Protect
may show a warning — expected for an unpublished APK.

## Multi-Device Synchronization Testing (Two Phones)

To verify multi-device synchronization end-to-end between Phone A and Phone B:

1. **Install APK on both Phone A and Phone B.**
2. **Sign into the SAME account on both phones** (via Email/Password or Phone OTP).
3. **Phone A Offline Creation:**
   - Put Phone A into Airplane Mode.
   - Ingest / create an order (e.g. "Nakul 2 food parcels Bhopal 500 COD").
   - Confirm order. Notice "Operations pending sync: 1" in Settings and on Dashboard.
4. **Phone A Reconnect:**
   - Disable Airplane Mode on Phone A.
   - SyncEngine automatically triggers sync upon network restoration (or tap **Sync now**).
   - "Operations pending sync" drops to 0, and status shows `ONLINE (✓ All changes synced)`.
5. **Phone B Initial Sync / Realtime Ingestion:**
   - Open Phone B while connected to internet.
   - The order created on Phone A immediately appears on Phone B via Firestore synchronization into Room.
6. **Phone B Edit → Phone A Update:**
   - On Phone B, update the order status to `COMPLETED`.
   - On Phone A, the status updates in real time to `COMPLETED`.
7. **Phone A Delete → Phone B Propagation:**
   - On Phone A, delete the order.
   - The deletion tombstone synchronizes and removes the order from Phone B without data recreation.

## Firestore Console Security Rules

Ensure `firestore.rules` is deployed in your Firebase console:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

