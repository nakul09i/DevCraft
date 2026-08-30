# Message Ingestion — WhatsApp Share, SMS, and why OTP is a different thing

DevCraft has **two independent ingestion channels** and **one unrelated
authentication channel**. Conflating them is the most common way this gets
described wrongly, so they are separated explicitly here.

| System | Purpose | Direction | Requires network |
| :--- | :--- | :--- | :--- |
| **Channel A** — WhatsApp Share | order capture | user pushes a message in | No |
| **Channel B** — SMS receiver | order capture | phone receives customer SMS | No |
| **Firebase phone OTP** | login only | Firebase sends *you* a code | Yes, during verification |

Firebase OTP is **not** an ingestion channel. It never creates a `MessageEntity`
and never touches an order. See [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

---

## Channel A — WhatsApp Share

```
WhatsApp → long-press message → Share → DevCraft
        → ACTION_SEND (text/plain)
        → MessageEntity(source = WHATSAPP_SHARE)
        → DeterministicParser
        → Review & edit
        → Order + OrderItems
```

**Verified present in the merged manifest of the built APK:**
- `android.intent.action.SEND` with `android:mimeType="text/plain"`
- `android:launchMode="singleTask"` plus `onNewIntent`, so sharing into an
  already-running DevCraft works as well as a cold start

Source metadata is stored as `WHATSAPP_SHARE`. The original text is kept
verbatim in `MessageEntity.originalText` and never mutated.

### What DevCraft explicitly does NOT do

**There is no automatic WhatsApp inbox access, and none is attempted.**

WhatsApp message history lives in that app's private storage. Reading it would
require root or exploiting the device. DevCraft does neither. The user chooses
each message and shares it deliberately.

Anyone claiming a normal Android app can silently read a WhatsApp inbox is
describing either malware or a fiction.

| Approach | Status |
| :--- | :--- |
| Share Intent (`ACTION_SEND`) | ✅ implemented — the supported path |
| Reading WhatsApp's database | ❌ never — private storage |
| `NotificationListenerService` scraping | ❌ not implemented; fragile, truncated text, invasive |
| WhatsApp Business Cloud API | ❌ out of scope; needs a business account and a server |

---

## Channel B — SMS receiver

```
Customer SMS arrives on the phone's own SIM
        → SmsReceiver (SMS_RECEIVED broadcast)
        → authentication-SMS filter
        → MessageEntity(source = SMS)
        → the same DeterministicParser
        → the same review and order flow
```

### Technical feasibility — yes, without being the default SMS app

This is the question that usually gets answered wrongly:

| Capability | Needs default-SMS-handler status? |
| :--- | :--- |
| Observe `SMS_RECEIVED` broadcast | **No** — `RECEIVE_SMS` permission is enough |
| Receive `SMS_DELIVER` | Yes |
| Write to the SMS content provider | Yes |
| Send SMS as the system messenger | Yes |

DevCraft only observes `SMS_RECEIVED`, so it works as a normal app. Verified in
the shipped APK:

```
uses-permission: name='android.permission.RECEIVE_SMS'
receiver: com.devcraft.sms.SmsReceiver
intent-filter: android.provider.Telephony.SMS_RECEIVED
```

Configuration:
- `targetSdk` 34, `minSdk` 26 — `SMS_RECEIVED` is unchanged across this range
- `exported="true"` — required, it is a system broadcast
- `android:permission="android.permission.BROADCAST_SMS"` — only the OS can
  deliver it, so another app cannot forge a fake incoming order
- `goAsync()` + an IO coroutine, so Room work is not done on the main thread
- Multipart SMS is reassembled from its PDUs before parsing

### Permission is opt-in, never at startup

`RECEIVE_SMS` is requested only when the merchant taps **Enable** next to
*"SMS order capture"* on the dashboard. Requesting a restricted permission at
first launch is both hostile and a policy problem.

If the permission is never granted, the receiver simply never fires. Channels A
and manual paste are unaffected.

### No GSM hardware. At all.

The phone's own SIM and modem receive the SMS. There is no external GSM module,
and there never needs to be. Keep two ideas apart:

- **GPS / location** → where the device is (used by the mapping layer)
- **GSM / SIM** → cellular connectivity and SMS

Neither SMS ingestion nor Firebase phone OTP requires add-on hardware.

### Play Store policy — the real constraint

`RECEIVE_SMS` is a **restricted permission**. Google Play only grants it to apps
whose core function is SMS handling, or that hold an approved exception. DevCraft
is an order manager, not an SMS app, so a Play submission using this permission
would very likely be **rejected** unless an exception is granted.

Practical consequence:

| Distribution | SMS ingestion |
| :--- | :--- |
| Sideloaded APK / hackathon demo / internal deployment | Works |
| Google Play public listing | Needs a declaration + exception, likely refused |

**Recommendation:** ship SMS capture as an opt-in feature for direct/enterprise
installs, and treat **WhatsApp Share + manual paste as the guaranteed ingestion
methods**. That is exactly how it is built — Channel B is additive and nothing
depends on it.

### Authentication SMS is filtered out

DevCraft's own login OTP arrives by SMS on the same device. Without a filter it
would land in the order inbox and get parsed as an order — the precise mixing the
brief warns against.

`SmsReceiver.looksLikeVerificationCode()` drops a message when it contains
`otp`, `one time password`, `verification code`, `security code`, `login code`,
`auth code`, `2fa`, `do not share`, `never share` (case-insensitive), or when the
whole body is 8 or fewer digits.

Covered by 7 unit tests, including Firebase's own message shape
(`"123456 is your verification code for devcraft-by-neutron.firebaseapp.com"`)
and negative cases proving real Hinglish and Devanagari orders are *not* filtered.

Known limitation: bank and delivery notifications are not filtered and will land
in the inbox as low-confidence messages. The merchant deletes them. A sender
allow-list would fix this and is not implemented.

---

## Shared pipeline

Both channels converge on one component, `MessageIngestor`:

```kotlin
suspend fun ingest(text, source, sender, senderName): String?
```

It parses, writes the `MessageEntity`, and appends the operation-log row **in a
single Room transaction**. There is exactly **one parser** and one pipeline; the
channel only changes the `source` value (`WHATSAPP_SHARE`, `SMS`, `MANUAL`).

`DeterministicParser` makes no network call. Neither does `MessageIngestor`.
Ingestion and parsing work fully offline, before and independently of any login.

---

## Test status

| Item | Status | How |
| :--- | :--- | :--- |
| `ACTION_SEND` registered | ✅ verified | merged manifest of built APK |
| WhatsApp Share end-to-end | 🟡 code complete | needs a phone |
| Manual paste | 🟡 code complete | needs a phone |
| SMS receiver registered | ✅ verified | merged manifest + `aapt2 dump badging` |
| SMS delivery end-to-end | 🟡 code complete | needs a phone with a SIM |
| OTP/order separation | ✅ 7 tests | `SmsReceiverTest` |
| Offline parsing | ✅ 21 tests | no network in the parser |
| Message → order conversion | ✅ 7 tests | `MessagePipelineTest` |

Nothing here is claimed as device-verified. No phone was available.

### Testing SMS without a real SIM

```bash
# Emulator: send a fake inbound SMS
adb emu sms send +919876543210 "Ramesh bhaiya ko kal 10 bori cement bhejo Rs 3500"
```

It should appear in the Message Inbox tagged `SMS`, already parsed. Then send an
OTP-shaped message and confirm it does **not** appear:

```bash
adb emu sms send +919876543210 "445566 is your verification code"
```
