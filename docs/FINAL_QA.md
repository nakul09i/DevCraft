# DevCraft — Final QA

Every row records what was **actually executed**, and every unverified row says so.

Environment: Windows 11, JDK 17.0.20 (Microsoft), Gradle 8.6, AGP 8.2.2,
Kotlin 1.9.22, Android SDK platform 34 / build-tools 34.0.0. No physical device
and no emulator were available.

---

## Executed

| # | Check | Command | Result |
| :-- | :--- | :--- | :--- |
| 1 | Clean compile | `./gradlew compileDebugKotlin` | **PASS** |
| 2 | Debug APK | `./gradlew assembleDebug` | **PASS** |
| 3 | Unit tests | `./gradlew testDebugUnitTest` | **PASS** — 71 tests, 0 failures |
| 4 | APK identity | `aapt2 dump badging` | **PASS** — `com.neutron.devcraft`, v1.0, minSdk 26, targetSdk 34 |
| 5 | `ACTION_SEND` registered | merged manifest inspection | **PASS** — `SEND` + `text/plain` + `singleTask` |
| 6 | SMS receiver registered | merged manifest + badging | **PASS** — `RECEIVE_SMS`, `SmsReceiver`, `SMS_RECEIVED` |
| 7 | Brand assets packaged | APK zip listing | **PASS** — 5 drawables present |
| 8 | Secret scan | grep over all tracked files | **PASS** — no keys or credentials |
| 9 | Generated files untracked | `git ls-files` | **PASS** — no `.gradle/`, `build/`, `local.properties` |
| 10 | Checksum | `Get-FileHash -Algorithm SHA256` | **PASS** — recorded in `release/SHA256SUMS.txt` |
| 11 | Build without Firebase config | `assembleDebug`, no `google-services.json` | **PASS** — plugin skipped, build green |
| 12 | Build without Mappls key | `assembleDebug`, blank `MAPPLS_API_KEY` | **PASS** — no HTTP attempted |

### Test suites

| Suite | Tests | Covers |
| :--- | :--: | :--- |
| `DeterministicParserTest` | 14 | quantities, Devanagari, dates, amounts, boundaries |
| `MessagePipelineTest` | 7 | ingestion → parse → order conversion |
| `MappingRepositoryTest` | 15 | Mappls parsing, HTTP error classification, offline |
| `MergeEngineTest` | 10 | 3 conflict scenarios, all permutations |
| `OperationalCalendarTest` | 7 | date windows, month/year boundaries |
| `PhoneAuthRepositoryTest` | 7 | E.164 validation, failure classification |
| `SmsReceiverTest` | 7 | OTP-vs-order separation |
| **Total** | **71** | **0 failures** |

---

## Not executed — requires a physical device

| # | Check | Blocker |
| :-- | :--- | :--- |
| 13 | APK install & launch | no device/emulator |
| 14 | WhatsApp Share → inbox | no device |
| 15 | **Cold-start share** (force-stop, then share) | no device — highest-risk item; the race was fixed but is unverified |
| 16 | Manual paste → order | no device |
| 17 | SMS received → inbox | no device with a SIM |
| 18 | Notification permission prompt (API 33+) | no device |
| 19 | Due-date alert delivery | no device |
| 20 | Notification tap → order detail | no device |
| 21 | Alarm survives reboot | no device |
| 22 | Airplane-mode CRUD + search | no device |
| 23 | Force-stop → reopen, data intact | no device |
| 24 | Room migration v2→v3 on real data | no device |
| 25 | DAO SQL execution | needs an instrumented test |

Offline behaviour is **statically** verified — no HTTP client is reachable from
ingest → parse → convert → query — but not runtime-verified.

---

## Not executed — requires configuration

| # | Check | Missing |
| :-- | :--- | :--- |
| 26 | Phone OTP send | `app/google-services.json` |
| 27 | OTP verify → dashboard | same |
| 28 | Session survives offline restart | same |
| 29 | Sign out / sign in again | same |
| 30 | Mappls geocoding live | `MAPPLS_API_KEY` |
| 31 | Mappls routing live | same |

---

## Cannot be executed — inputs absent

| # | Check | Missing |
| :-- | :--- | :--- |
| 32 | Parser accuracy score | `messages_train.json` |
| 33 | Schema conformance | `schema.json` |
| 34 | Official scoring | `score.py` |
| 35 | Batch export format | `sample_submission.json` |
| 36 | Dataset date/clarification rules | `DATASET_CARD.md` |

`competition/` is an empty directory. **No parser score is reported**, because
none can be computed.

---

## Not applicable

| Item | Why |
| :--- | :--- |
| `npm install` / `lint` / `test` / `build` | No Node project — 0 TS files, no `package.json` |
| PWA / service worker / manifest | Native Android APK |
| Capacitor | Nothing to wrap; no web build |
| Firebase Hosting | No web artifact to host |
| IndexedDB | Room/SQLite fills this role |
| TypeScript errors | 0 TypeScript files |
| Release APK | No signing keystore configured — deliberately not fabricated |

---

## Defects found and fixed this cycle

| Defect | Impact before fix |
| :--- | :--- |
| `TextAlign` passed to `Text(style=)` | **The project did not compile at all** |
| No Gradle wrapper | every documented build command failed |
| `app/build/` committed (1,547 files) | a stale APK hid the fact the tree never compiled |
| Quantity via substring `contains` | `"10 bori"` → 1; `"Rs 2500"` → quantity 2 |
| Date keywords ASCII-only | `"आज ही चाहिए"` → no due date |
| Tokenizer dropped `\p{M}` | `परसों` shredded to `परस`; `बोरी` to `बोर` |
| Conversion not in a transaction | crash mid-convert → orphaned rows |
| `deviceId` regenerated per ViewModel | operation log attribution meaningless |
| `flow { emit(oneShot) }` for order detail | status edits never appeared |
| `POST_NOTIFICATIONS` never requested | every alert silently dropped on API 33+ |
| Inexact alarms; no reboot re-arm | reminders late or lost on restart |
| Notification had no `contentIntent` | tapping it did nothing |
| `fallbackToDestructiveMigration()` | a missed migration would wipe all orders |
| `DeterministicConflictResolver` dead code | Conflicts screen could only ever be empty |
| README claimed WorkManager sync + HLC | documentation asserted features that did not exist |

---

## Sign-off

**Green:** build, 71 unit tests, debug APK, checksum, secret scan, repository
hygiene, graceful degradation with neither Firebase nor Mappls configured.

**Not signed off:** anything requiring a phone, live OTP, live Mappls, or the
competition dataset.

The single highest-value next action is installing
`release/DevCraft-Master-debug.apk` on a real phone and working checklist items
13–25. That converts the largest block of unverified items into verified ones, or
finds real bugs.
