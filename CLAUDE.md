# CLAUDE.md - DevCraft System Specification & Audit Handoff

> **Authoritative Handoff Document for Future Claude Code Premium Audits**  
> **Repository:** `DevCraft`  
> **Primary Platform:** Native Android (Kotlin / Jetpack Compose)

---

## 1. Project Purpose
DevCraft is a native Android, offline-first smart order management application built to empower merchants and businesses to capture, parse, track, and synchronize conversational and manual orders seamlessly.

The core operational flow is:
$$\text{Understand} \longrightarrow \text{Save Locally} \longrightarrow \text{Work Offline} \longrightarrow \text{Sync} \longrightarrow \text{Resolve Conflicts} \longrightarrow \text{Query}$$

---

## 2. Official DevCraft Requirements
- **100% Offline Core Path:** Cold starts, CRUD operations, global search, prior-order lookups, and operational queries must never block on or require network connectivity.
- **Multilingual Conversational Parser:** Modular natural language parser extracting structured order JSON from English, Hindi, Hinglish, Roman Hindi, and Devanagari.
- **Operation-Log Architecture:** Every local mutation appends an immutable local operation record to maintain a change diary for synchronization.
- **Deterministic Conflict Convergence:** Simultaneous edits across multiple devices must converge deterministically regardless of network reconnection order; conflicting edits are logged to a dedicated `Conflict` entity.
- **Secondary Cloud Sync:** Firebase Firestore / REST serves purely as a synchronization transport and multi-device relay, never as the primary local data store.
- **Local Alert System:** Due-date notifications scheduled locally on-device without external telecom/GSM dependencies.

---

## 3. Technology Stack
- **Language:** Kotlin 2.0+
- **UI Framework:** Jetpack Compose (Material 3)
- **Local Database:** Room Persistence Library (SQLite)
- **Asynchronous Execution:** Kotlin Coroutines & Flow / StateFlow
- **Background Tasks:** Android Jetpack WorkManager
- **Network / Transport:** Retrofit 2 + OkHttp 3 & Firebase Firestore SDK (Sync Relay)
- **Serialization:** Kotlinx Serialization / Google Gson
- **Dependency Injection:** Hilt / Koin (or Manual Clean Dependency Containers)
- **Target OS:** Android API 26 (Android 8.0 Oreo) to API 35 (Android 15)

---

## 4. Current Repository Structure
```text
DevCraft/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/devcraft/
│   │   │   │   ├── core/                  # Base dispatchers, utilities, Result wrapper
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/             # Room DB, DAOs, Entity definitions
│   │   │   │   │   ├── remote/            # Firebase Firestore & REST sync adapters
│   │   │   │   │   └── repository/        # Offline-first repository implementations
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/             # Pure Kotlin domain data models
│   │   │   │   │   └── usecase/           # Business logic & operational use cases
│   │   │   │   ├── parser/
│   │   │   │   │   ├── common/            # Normalizers, tokenizers, linguistic dictionaries
│   │   │   │   │   ├── offline/           # Deterministic rule-based multilingual parser
│   │   │   │   │   └── online/            # Optional cloud/LLM parser fallback
│   │   │   │   ├── sync/
│   │   │   │   │   ├── engine/            # SyncEngine, WorkManager SyncWorker
│   │   │   │   │   ├── conflict/          # 3-Way & field-level merge algorithm
│   │   │   │   │   └── operations/        # OperationLogManager, Journal replay
│   │   │   │   ├── alerts/
│   │   │   │   │   ├── scheduler/         # AlarmManager / WorkManager alert scheduler
│   │   │   │   │   ├── notification/      # Android Notification Manager wrapper
│   │   │   │   │   └── adapter/           # Clean adapter boundary (Local, SMS, GSM)
│   │   │   │   └── ui/                    # Jetpack Compose Screens & ViewModels
│   │   │   └── AndroidManifest.xml
│   │   └── test/                          # Unit tests (Parser, Offline DB, Sync)
│   └── build.gradle.kts
├── competition/                           # Official competition datasets & scripts
├── tests/                                 # Integration & end-to-end test suites
├── build.gradle.kts
├── settings.gradle.kts
├── CLAUDE.md
├── ARCHITECTURE.md
├── DECISIONS.md
└── README.md
```

---

## 5. Android Architecture
- **Clean Architecture + MVVM / MVI Pattern:** Strict layer separation (UI -> Domain -> Data).
- **Unidirectional Data Flow (UDF):** Compose UI observes `StateFlow` from ViewModels and emits user intents/actions.
- **Repository Pattern:** UI only interacts with Repositories via Domain Use Cases; Repositories mediate between local Room DAOs and the remote sync engine.

---

## 6. Room / SQLite Data Model

### Entities:
1. **`CustomerEntity`**
   - `customerId: String (PK)`
   - `name: String`, `phone: String?`, `address: String?`, `notes: String?`
   - `createdAt: Long`, `updatedAt: Long`
2. **`OrderEntity`**
   - `orderId: String (PK)`
   - `customerId: String (FK)`
   - `status: String` (DRAFT, CONFIRMED, PROCESSING, COMPLETED, CANCELLED)
   - `totalAmount: Double?`, `dueDate: Long?`, `rawMessage: String?`
   - `referencesPriorOrder: Boolean`, `createdAt: Long`, `updatedAt: Long`
3. **`OrderItemEntity`**
   - `itemId: String (PK)`
   - `orderId: String (FK)`
   - `description: String`, `quantity: Int`, `unitPrice: Double?`, `attributesJson: String`
4. **`OperationEntity` (Change Diary)**
   - `operationId: String (PK)`
   - `deviceId: String`, `entityType: String` (CUSTOMER, ORDER, ITEM)
   - `entityId: String`, `operationType: String` (CREATE, UPDATE, DELETE)
   - `changedFieldsJson: String`, `hlcTimestamp: Long`, `logicalClock: Long`
   - `syncStatus: String` (PENDING, IN_FLIGHT, SYNCED, FAILED)
   - `createdAt: Long`
5. **`ConflictEntity` (Conflict Record)**
   - `conflictId: String (PK)`
   - `entityId: String`, `entityType: String`, `field: String`
   - `localValue: String?`, `remoteValue: String?`, `winningValue: String?`
   - `resolutionReason: String`
   - `createdAt: Long`, `resolvedAt: Long?`

---

## 7. Parser Architecture

### Output JSON Schema:
```json
{
  "customer": "string | null",
  "items": [
    {
      "description": "string",
      "quantity": 1,
      "attributes": {}
    }
  ],
  "due_date": "YYYY-MM-DD | null",
  "amount": 0.0,
  "references_prior_order": false,
  "confidence": 0.95,
  "needs_clarification": false
}
```

### Multilingual Support & Rules:
- **Linguistic Coverage:** English, Hindi (Devanagari), Hinglish / Roman Hindi (e.g., *"Ramesh ko kal 5 bori cement chahiye"*).
- **Date Engine:** Resolves relative offsets (*parso, agle somvar, tomorrow, next week, kal shaam*).
- **Number & Quantity Normalizer:** Handles Hindi number words (*ek, do, teen, paanch, das, dedh, dhai, sau, hazaar*) and Devanagari numerals (`०-९`).
- **Context & Prior-Order Resolver:** Recognizes repeat references (*"same as last time"*, *"wahi purana maal"*).
- **Ambiguity Guard:** Sets `needs_clarification: true` with `confidence < 0.7` if quantities or customer identity cannot be deterministically inferred.

---

## 8. Offline Requirements
1. **Cold Start:** Starts immediately using local SQLite database; zero network calls on launch.
2. **Crash Resilience:** All mutations committed inside atomic Room transactions.
3. **Reboot Persistence:** Changes remain intact across OS reboot and process termination.
4. **Offline Search:** Local FTS4 / SQL `LIKE` queries provide instant search over orders, customers, and message history.

---

## 9. Operation Log Design
- **Append-Only Journal:** Every create/update/delete operation writes to `OperationEntity`.
- **Hybrid Logical Clocks (HLC):** Combines physical wall-clock time with a monotonically increasing logical counter to guarantee strict causal ordering across distributed devices.
- **Field-Level Diffing:** `changedFieldsJson` stores only mutated fields (e.g., `{"dueDate": 1788220800000}`) rather than entire monolithic records.

---

## 10. Sync Engine Design
- **WorkManager Integration:** `SyncWorker` triggers on `NetworkType.CONNECTED` with exponential backoff retry.
- **Two-Phase Push/Pull:**
  1. **Push:** Sends un-synced operations (`syncStatus = PENDING`) to remote endpoint.
  2. **Pull:** Fetches remote operations with HLC greater than the last recorded sync watermark.
- **Idempotency:** Operations are identified by unique UUID `operationId`; re-transmitting an already processed operation is a no-op.

---

## 11. Deterministic Conflict Resolution Policy
- **Disjoint Fields (Clean Merge):** If Device A updates `due_date` and Device B updates `amount`, both edits merge cleanly into Room.
- **Same-Field Collisions:**
  1. Compare **Hybrid Logical Clock (HLC)** timestamps. Higher HLC wins.
  2. If HLC is identical (exact concurrent collision), break ties deterministically using `deviceId` lexical comparison: `deviceId_A > deviceId_B`.
  3. The losing value is **never silently discarded** - a row is immediately logged in `ConflictEntity` for merchant inspection.

---

## 12. Firebase / Backend Architecture
- **Role:** Synchronization transport and cloud relay.
- **Collections:**
  - `workspaces/{workspaceId}/operations/{operationId}`
  - `workspaces/{workspaceId}/devices/{deviceId}`
- **Offline Fallback:** If Firebase SDK fails to connect or returns errors, the local app proceeds without interruption.

---

## 13. Local Alert Architecture
- **Scheduler:** Uses Android `AlarmManager.setExactAndAllowWhileIdle` for exact delivery or `WorkManager` for flexible reminder windows.
- **Notifications:** Posts to high-priority Notification Channels with direct deep-links to `OrderDetailScreen`.

---

## 14. GSM / SMS Future Adapter Boundary
```kotlin
interface AlertChannelAdapter {
    suspend fun sendAlert(recipient: String, message: String): AlertDeliveryResult
}

class LocalNotificationAdapter(val context: Context) : AlertChannelAdapter { ... }
class AndroidSmsAdapter(val context: Context) : AlertChannelAdapter { ... } // Future
class HardwareGsmAdapter(val serialPort: String) : AlertChannelAdapter { ... } // Future
```

---

## 15. Testing Commands
- **Local Unit Tests:**
  ```powershell
  ./gradlew test
  ```
- **Parser Linguistic Test Suite:**
  ```powershell
  ./gradlew testDebugUnitTest --tests "com.devcraft.parser.*"
  ```
- **Sync & Conflict Tests:**
  ```powershell
  ./gradlew testDebugUnitTest --tests "com.devcraft.sync.*"
  ```
- **Instrumented UI / Room Tests:**
  ```powershell
  ./gradlew connectedAndroidTest
  ```

---

## 16. Build Commands
- **Compile Debug APK:**
  ```powershell
  ./gradlew assembleDebug
  ```
- **Compile Release APK:**
  ```powershell
  ./gradlew assembleRelease
  ```
- **Clean Build:**
  ```powershell
  ./gradlew clean
  ```

---

## 17. APK Generation Instructions
1. Run `./gradlew assembleDebug`.
2. Locate the generated APK at:
   `app/build/outputs/apk/debug/app-debug.apk`
3. Install on target device or emulator:
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`

---

## 18. Competition Dataset Files & Purpose
| File | Required Purpose |
| :--- | :--- |
| `schema.json` | Benchmark schema definition for parser validation |
| `messages_train.json` | Training corpus of multilingual order messages |
| `DATASET_CARD.md` | Data distribution, annotations, and linguistic taxonomy |
| `score.py` | Official evaluation script scoring parser accuracy & F1 metrics |
| `sample_submission.json` | Reference output format for submission verification |
| `conflict_scenarios.md` | Edge-case scenarios for validating multi-device sync convergence |

---

## 19. Known Bugs
- *None currently logged (Clean repository setup).*

---

## 20. Known Limitations
- Host environment requires JDK 17+ and Android SDK Command-line Tools configured in PATH for local APK compilation.
- Hardware GSM transmission is intentionally stubbed behind `AlertChannelAdapter` pending external modem driver integration.

---

## 21. Implementation TODO List
- [ ] Install JDK 17/21 and configure Android SDK build environment.
- [ ] Initialize Android root build scripts (`build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`).
- [ ] Implement Room entities, DAOs, and `DevCraftDatabase`.
- [ ] Build offline deterministic multilingual parser (English, Hindi, Hinglish, Devanagari).
- [ ] Implement `OperationLogManager` and field-level deterministic `ConflictResolver`.
- [ ] Build Jetpack Compose UI screens (Dashboard, Orders, Customers, Parser, Sync).
- [ ] Implement `LocalAlertScheduler` and Notification Manager.
- [ ] Run parser benchmark validation and compile debug APK.

---

## 22. Areas Requiring Careful Review
1. **Clock Skew:** Ensure Hybrid Logical Clock safely handles local system clock rollbacks.
2. **Hinglish Token Ambiguity:** Tokenization of colloquial terms where dates and item names overlap.
3. **Database Migration Safety:** Maintain non-destructive Room migration policies.
4. **Sync Queue Lock Contention:** Ensure UI write transactions do not block on background sync uploads.

---

## 23. Rules That Must Never Be Violated
1. **NEVER require internet connectivity for the core critical path.**
2. **NEVER make Room/SQLite secondary to Firebase or any remote database.**
3. **NEVER perform blind last-write-wins without field-level merging and conflict logging.**
4. **NEVER rewrite or replace the native Android (Kotlin/Compose) architecture with a Web/React/PWA wrapper.**
5. **NEVER commit secrets, private keys, or API credentials to the repository.**
