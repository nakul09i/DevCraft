# DevCraft

> **Native Android, Offline-First Smart Order Management Application**  
> *Built with Kotlin, Jetpack Compose, Room (SQLite), Coroutines, Flow, and WorkManager.*

---

## 🚀 Overview

DevCraft is an intelligent, offline-first mobile application designed to streamline conversational order processing for merchants and micro-enterprises. It takes messy, natural language customer requests (in English, Hindi, Hinglish, and Devanagari) and transforms them into structured, actionable orders that work completely offline, sync seamlessly across devices, and resolve conflicts deterministically.

```
Understand ──> Save Locally ──> Work Offline ──> Sync ──> Resolve Conflicts ──> Query
```

---

## 🏗️ Architecture & Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin 2.0+ |
| **UI Framework** | Jetpack Compose (Material 3) with Unidirectional Data Flow |
| **Local Storage** | Room Persistence Library / SQLite (Single Source of Truth) |
| **Concurrency** | Kotlin Coroutines, Flow & StateFlow |
| **Background Sync** | Android Jetpack WorkManager |
| **Cloud Transport** | Firebase Firestore / REST API (Secondary Sync Relay) |
| **Architecture** | Clean Architecture (UI -> Domain -> Data) |

---

## 🛠️ Setup & Prerequisites

1. **Java Development Kit (JDK):** OpenJDK 17 or 21.
2. **Android SDK:** Command-line Tools / Android SDK Platform 34+.
3. **Gradle:** Version 8.4+ (included via Gradle Wrapper `./gradlew`).
4. **Target Device:** Android device or emulator running Android 8.0 (API 26) or higher.

---

## ⚡ Run & Build Instructions

### 1. Run Unit & Parser Tests
```powershell
./gradlew test
```

### 2. Run Sync & Conflict Simulation Tests
```powershell
./gradlew testDebugUnitTest --tests "com.devcraft.sync.*"
```

### 3. Build Debug APK
```powershell
./gradlew assembleDebug
```
The resulting APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 4. Install on Connected Device
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📴 Offline Mode Demo Walkthrough

1. **Cold Start Without Network:**  
   Launch DevCraft in Airplane Mode. The dashboard, customer lists, order records, and search queries load instantly from local Room SQLite database with 0ms network latency.
2. **Create Offline Orders:**  
   Input a multilingual conversational order (e.g., *"Ramesh Bhai ko kal shaam 10 bori cement bhejo"*). The parser extracts the customer, quantity, unit, and relative due date offline and saves it immediately to Room.
3. **App Kill & Reboot Survival:**  
   Force-close the app and reboot the device. Reopen the app—all pending orders and local operation diaries remain fully intact.

---

## 🔄 Multi-Device Sync Demo Walkthrough

1. **Offline Edits on Multiple Devices:**  
   - **Device A (Offline):** Edits Order #101 due date to `2026-09-05`.
   - **Device B (Offline):** Edits Order #101 total amount to `₹4,500`.
2. **Reconnecting to Network:**  
   Both devices regain connectivity. `WorkManager` activates `SyncWorker` in the background.
3. **Non-Conflicting Field Merge:**  
   The sync engine performs a field-level 3-way merge. Order #101 on both devices automatically converges to:
   - Due Date: `2026-09-05`
   - Total Amount: `₹4,500`

---

## ⚔️ Deterministic Conflict Demo Walkthrough

1. **Concurrent Same-Field Collision:**  
   - **Device A (Offline):** Updates Order #102 status to `COMPLETED` at HLC `(T=100, C=1, Dev=A)`.
   - **Device B (Offline):** Updates Order #102 status to `CANCELLED` at HLC `(T=105, C=1, Dev=B)`.
2. **Deterministic Resolution:**  
   Regardless of whether Device A or Device B reconnects first:
   - Device B has the higher Hybrid Logical Clock timestamp.
   - Winning Value applied: `CANCELLED`.
3. **Transparent Conflict Audit:**  
   The losing state (`COMPLETED` from Device A) is recorded in the `Conflict` database table. The merchant can view the conflict audit history under **Settings > Sync & Conflicts**.

---

## 📄 License & Compliance

Licensed under the Apache License, Version 2.0. Clean architecture without proprietary vendor lock-in.
