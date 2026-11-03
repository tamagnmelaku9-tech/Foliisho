# Real-Time Cloud Synchronization Plan: Vercel Web Admin & Standalone Android App

Connect the live Web Admin Dashboard deployed at `https://foliisho.vercel.app/` and the standalone Android Driver App via a live, zero-barrier Firebase Realtime Database cloud sync endpoint. All coordinator actions (advancing queues, announcing permitted vehicle categories, updating fuel availability) will propagate instantly across the network to all physical Android phones without manual refresh.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions have been aligned based on your preferences:

- **Confirmed Decision 1 (Real-Time Cloud Backend)**: Firebase Realtime Database endpoint (`https://hawassa-transit-default-rtdb.firebaseio.com/stations.json` & custom Firebase URL support). This provides immediate bidirectional synchronization, native Server-Sent Events (SSE) streaming for Android, and standard REST `PATCH`/`PUT` for static web hosting platforms like Vercel with zero server maintenance.
- **Confirmed Decision 2 (Database Provisioning & Access)**: Instant public cloud sync endpoint with open rules for station queue channels, enabling immediate out-of-the-box live testing between `https://foliisho.vercel.app/` and physical Android APK installations without complex OAuth or service account token exchanges.
- **Confirmed Decision 3 (Network Synchronization Strategy)**: Direct live WebSocket/SSE streaming listener combined with automated background fallback polling (4-second interval) to guarantee resilience across Hawassa's mobile cellular networks (Ethio Telecom 3G/4G).

---

### 1. Overview & Core Concept

Currently, `https://foliisho.vercel.app/` operates as a static client on Vercel without a persistent global database bridge, meaning coordinator actions do not propagate to standalone Android devices.

This update resolves the disconnect by establishing a persistent cloud state hub:
1. **Station Coordinator (Vercel)**: When a manager at Benzene Madya clicks **"Call Next Driver"**, **"Permit Category"**, or toggles **"Fuel Stock"**, the Web Admin sends an asynchronous HTTP `PATCH` directly to the live Firebase cloud database.
2. **Global Real-Time Database**: Stores the canonical state for all Hawassa stations (Benzene Madya, Total Menahariya, NOC Piassa, Ola Datto, Tabor Oil).
3. **Android Driver App**: Maintains an open streaming listener (`EventSource`/SSE) with the cloud endpoint. When changes occur, the phone receives updates within milliseconds, decrements the headway counter, and triggers the bilingual Amharic/English acoustic turn warning as the driver approaches their turn.

---

### 2. User Experience & Visual Design

- **Vercel Web Admin Dashboard (`https://foliisho.vercel.app/`)**:
  - **Live Cloud Health Indicator**: Top-bar pill badge displaying `🟢 Live Cloud Database: Connected (RTDB)`.
  - **Configurable Database URL**: Modal permitting coordinators to switch between the default live test endpoint or their own custom Firebase/Supabase project URL.
  - **Audit Status Bar**: Instant visual feedback confirming each cloud push (e.g., `✓ Cloud Synced: Bajaj permitted at Benzene Madya (12:45:10)`).
- **Standalone Android Driver App**:
  - **Zero-Latency Banner Updates**: Station cards dynamically display the coordinator's announced vehicle category in bold Amharic & English immediately upon update.
  - **Real-Time Headway Counter**: "Vehicles Ahead" and "Estimated Wait Time" update live as the coordinator calls drivers.
  - **Autonomous 10-Minute Turn Warning**: High-priority alarm with loud siren tone, vibration pattern, and natural voice utterance:
    - **Amharic**: *"ተራዎ ሊደርስ 10 ደቂቃ ቀረዎት፤ ወደ ማድያ ይምጡ!"*
    - **English**: *"Your turn will arrive in 10 minutes; please come to the station!"*
  - **Network Status Chip**: Subtle header indicator confirming whether the app is on `Live Cloud Stream` or `Fallback Polling Mode`.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Direct Cloud Database vs. Custom Node.js Relay Server**:
  - *Chosen Approach*: Direct Firebase Realtime Database REST + SSE streaming endpoint.
  - *Why*: Vercel is a serverless/static frontend host; running a persistent Node.js WebSocket process on Vercel requires separate server infrastructure (like Render or Railway). Firebase Realtime Database provides native REST `PATCH` for the Vercel static frontend and native SSE streaming for the Android app without hosting costs or cold-start sleep delays.
- **Decision 2: Dual SSE Streaming + Fallback Polling**:
  - *Chosen Approach*: OkHttp Server-Sent Events client on Android backed by a periodic 4-second poll loop.
  - *Why*: If a Bajaj or taxi driver enters an area with fluctuating signal along Piazza or Menahariya Road, the streaming socket may silently drop. The fallback poll ensures state is never stale by more than a few seconds.

---

### 4. Technical Architecture & Data Strategy

```
 ┌─────────────────────────────────────────────────────────────┐
 │       Station Coordinator at https://foliisho.vercel.app/    │
 │            (Web Admin Dashboard on PC/Tablet)               │
 └──────────────────────────────┬──────────────────────────────┘
                                │ HTTPS PATCH / PUT
                                ▼
 ┌─────────────────────────────────────────────────────────────┐
 │           Firebase Realtime Database Cloud Hub               │
 │     https://hawassa-transit-default-rtdb.firebaseio.com/    │
 │       (Canonical Station State, Lanes, & Announcements)     │
 └──────────────────────────────┬──────────────────────────────┘
                                │ Live SSE Stream (Accept: text/event-stream)
                                │ + 4-sec Fallback Polling
                                ▼
 ┌─────────────────────────────────────────────────────────────┐
 │           Hawassa Transit Android App (Driver APK)          │
 │       - HawassaCloudSync.kt (OkHttp SSE Streaming Client)   │
 │       - QueueRepository.kt (Local State & Headway Engine)   │
 │       - NotificationAlertEngine.kt (Amharic Voice & Siren)  │
 └─────────────────────────────────────────────────────────────┘
```

#### Component & Implementation Tasks

1. **Web Admin Cloud Integration (`web-admin.html`, `web-admin/index.html`, `index.html`)**:
   - Point default API endpoint to the live cloud database URL with automatic localStorage persistence.
   - Implement direct `PATCH` requests for:
     - `adminSelectPermittedCategory(category)` -> updates `/stations/{stationId}/activePermittedCategory.json`
     - `adminCallNext()` -> updates `/stations/{stationId}/lanes/{category}/currentlyServing.json`
     - `adminToggleFuel(type)` -> updates `/stations/{stationId}/benzeneStatus.json` or `dieselStatus.json`
     - `adminBroadcastTenMinAlert()` -> writes timestamp to `/stations/{stationId}/broadcastAlert.json`
   - Add SSE listener so multi-coordinator setups see each other's updates in real time.

2. **Android Network & Streaming Engine (`HawassaCloudSync.kt`)**:
   - Implement streaming OkHttp listener connecting to the live cloud database via `GET /stations.json` with `Accept: text/event-stream`.
   - Parse incoming `put` and `patch` data events and immediately route them to `QueueRepository.syncStationStateFromCloud`.
   - Maintain 4-second fallback polling with graceful auto-recovery.

3. **Android App Compilation & Export (`HawassaTransitDriver.apk`)**:
   - Verify zero build warnings or lint errors with `compile_applet`.
   - Generate production-ready APK binary and serve via `/download/hawassa-transit-driver.apk`.
   - Update `DEPLOYMENT_GUIDE.md` with instructions on redeploying to Vercel and installing the APK on phones.
