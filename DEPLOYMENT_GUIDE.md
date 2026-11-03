# Hawassa Transit & Fuel Queue Management System
## Live Cloud Synchronization & Vercel Deployment Guide

This guide details how the **Web Admin Dashboard deployed on Vercel (`https://foliisho.vercel.app/`)** and the **Standalone Android Driver App** are linked together in real-time across the network.

---

### Architecture & Live Synchronization Overview

```
 ┌─────────────────────────────────────────────────────────────┐
 │       Station Coordinators on PC / Laptop Browser           │
 │               https://foliisho.vercel.app/                  │
 │   - Selects Hawassa stations (Benzene Madya, Total, etc.)   │
 │   - Sets & announces permitted vehicle category             │
 │   - Advances queues ("Call Next Driver")                    │
 │   - Toggles fuel availability (Benzene & Diesel)            │
 └──────────────────────────────┬──────────────────────────────┘
                                │ HTTPS PATCH & REST (/api/*)
                                ▼
 ┌─────────────────────────────────────────────────────────────┐
 │             Vercel Serverless & Cloud Database              │
 │   - Vercel Serverless Function (api/index.js)               │
 │   - Firebase Realtime Database (cloud-sync.js)              │
 │   - Broadcasts real-time events to all subscribers          │
 └──────────────────────────────┬──────────────────────────────┘
                                │ Live SSE Stream (Accept: text/event-stream)
                                │ + 4-Second Fallback Polling
                                ▼
 ┌─────────────────────────────────────────────────────────────┐
 │        Physical Android Phones (HawassaTransitDriver.apk)   │
 │   - HawassaCloudSync.kt: Instant WebSocket/SSE listener     │
 │   - Zero-latency category announcement banner updates       │
 │   - Real-time "Vehicles Ahead" headway countdown            │
 │   - Automatic 10-Minute Audible Turn Alert (Amharic/English)│
 └─────────────────────────────────────────────────────────────┘
```

---

### 1. How to Update & Redeploy on Vercel (`https://foliisho.vercel.app/`)

Your Vercel deployment has been updated with:
1. **Serverless API Bridge (`api/index.js`)**: All `/api/*` requests on `https://foliisho.vercel.app/` are now handled by the serverless backend, eliminating the 404 errors.
2. **Global Real-Time Sync (`cloud-sync.js`)**: Coordinates actions across Vercel and mobile clients using direct WebSocket/SSE streaming.
3. **Updated Routing (`vercel.json`)**:
   - `/` -> Unified Preview (`index.html`)
   - `/admin` -> Standalone Coordinator Web Admin (`web-admin.html`)
   - `/driver` -> Full-screen Driver Web App (`driver-app.html`)
   - `/api/(.*)` -> Serverless API (`api/index.js`)
   - `/download/(.*)` -> Android APK direct download

#### Redeployment Steps:
- **If connected via GitHub**: Simply push your code to GitHub. Vercel will automatically trigger a new deployment.
- **If using the Vercel CLI**:
  ```bash
  vercel --prod
  ```
- Your live Web Admin will be active at `https://foliisho.vercel.app/admin`.

---

### 2. How to Install the Rebuilt Android APK on Physical Phones

1. **Direct Download from Preview**:
   - In your browser, open `https://foliisho.vercel.app/download/hawassa-transit-driver.apk` (or click **"Download Android APK"** in the top navigation bar).
   - The compiled file `HawassaTransitDriver.apk` (~22 MB) will download to your device.
2. **Install on Phone**:
   - Tap the downloaded `.apk` file.
   - Allow "Install from unknown sources" if prompted by Android.
   - Tap **Install**.
3. **Launch & Verify**:
   - Open **"Hawassa Transit Queue"**.
   - The app's `HawassaCloudSync` service will immediately establish a live streaming connection with `https://foliisho.vercel.app/api`.

---

### 3. Step-by-Step Live Synchronization Verification

Follow these steps to verify real-time sync across devices:

1. **Open Web Admin on PC**:
   - Visit `https://foliisho.vercel.app/admin` on your computer or tablet.
   - The status badge at the top will indicate: `🟢 Live Cloud Stream: Connected`.
2. **Open Driver App on Android Phone**:
   - Launch the installed Android app on your phone.
   - Look at the station card for **"Benzene Madya Central"**.
3. **Test Permitted Category Change**:
   - On the PC Web Admin, click **"Taxi / Minibus"** or **"Public Bus"**.
   - **Result on Phone**: The mobile app instantly updates the announcement banner to:
     - Amharic: *"አሁን የተፈቀደው ተሽከርካሪ: ታክሲ / ሚኒባስ"*
     - English: *"Admin Called Category: Taxi / Minibus"*
4. **Test Queue Advancement**:
   - On the phone, enter license plate `AA-2-84920` and take a queue ticket.
   - On the PC Web Admin, click **"Call Next Driver (ቀጣይ ጥራ)"**.
   - **Result on Phone**: The "Vehicles Ahead" counter immediately drops from 8 to 7, and wait time adjusts.
5. **Test 10-Minute Audible Turn Alert**:
   - When the queue advances to within 10 minutes (or <= 3 vehicles ahead), or when the coordinator clicks **"Broadcast 10-Min Warning"**:
   - **Result on Phone**: The phone sounds a loud dual-tone emergency buzzer alarm, pattern vibration, and speaks the exact speech alert in the selected language:
     - Amharic: *"ተራዎ ሊደርስ 10 ደቂቃ ቀረዎት፤ ወደ ማድያ ይምጡ!"*
     - English: *"Your turn will arrive in 10 minutes; please come to the station!"*

---

### 4. Optional: Custom Firebase Realtime Database Configuration

If you prefer to link your own Firebase Realtime Database:
1. Go to [Firebase Console](https://console.firebase.google.com/) -> Create a project.
2. Navigate to **Build** -> **Realtime Database** -> **Create Database**.
3. In **Rules**, set `".read": true` and `".write": true` for testing.
4. Copy your database URL (e.g. `https://my-hawassa-queue-default-rtdb.firebaseio.com/`).
5. In the Web Admin, click **⚙️ API URL / Cloud DB** and paste your URL.
6. In `.env`, set:
   ```env
   HAWASSA_API_BASE_URL=https://my-hawassa-queue-default-rtdb.firebaseio.com
   ```
7. Both the web dashboard and mobile apps will synchronize via your dedicated Firebase database.
