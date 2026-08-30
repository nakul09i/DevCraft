# DevCraft — Install

## Artifact

| Field | Value |
| :--- | :--- |
| File | `DevCraft-Master-debug.apk` |
| Size | 21,485,696 bytes (20.5 MB) |
| SHA-256 | `738ae2c74fff1574c31fa8f0d267f9db989aeaa7a43ccc03286e6cac33174a6b` |
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

## Release APK

**Not produced.** No signing keystore is configured, and fabricating one would
give you an artifact you cannot reproduce or update. `app/build.gradle.kts` has
no `signingConfigs` block for release.

To produce one:

```powershell
keytool -genkeypair -v -keystore devcraft-release.jks `
  -keyalg RSA -keysize 2048 -validity 10000 -alias devcraft
```

Add a `signingConfigs` block reading the passwords from `local.properties`
(gitignored — never inline them), then `./gradlew assembleRelease`. Register the
new keystore's SHA-1 and SHA-256 in Firebase as well, or Phone Auth will work in
debug and fail in release.

## Note on distribution

The APK is **not committed to git** — `.gitignore` excludes `release/*.apk`. A
20 MB binary in version control is what made this repo's history misleading in
the first place (a stale committed APK hid the fact the tree did not compile).

Attach it to a GitHub Release instead:

```bash
gh release create v1.0 release/DevCraft-Master-debug.apk release/SHA256SUMS.txt \
  --title "DevCraft 1.0" --notes-file release/INSTALL.md
```

## First run

1. **Launch.** Without `google-services.json` the app opens straight to the
   Message Inbox — no login. With it configured, the login screen appears; you
   can sign in or tap **Continue offline without signing in**.
2. **Grant notifications** when prompted (Android 13+). Declining only disables
   due-date reminders.
3. **Share a message in:** open WhatsApp → long-press an order message → Share →
   **DevCraft**. It lands in the inbox, already parsed.
   Optionally enable **SMS order capture** on the dashboard to also ingest
   incoming customer SMS — see `docs/SMS_INGESTION.md` for the Play Store
   policy caveat.
4. Tap **Interpret** → review the extracted customer, date, amount and items →
   **Confirm & Create Room Order**.

Everything above works in airplane mode.
