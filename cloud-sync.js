/**
 * Hawassa Transit System - Real-Time Cloud Synchronization Engine
 * Links Vercel Web Admin (https://foliisho.vercel.app/) and Standalone Android Driver Apps
 * via Firebase Realtime Database & SSE Streaming
 */

const DEFAULT_FIREBASE_RTDB_URL = "https://foliisho-default-rtdb.firebaseio.com";
const LOCAL_BUS_NAME = "hawassa_queue_bus";

class HawassaCloudSyncClient {
  constructor() {
    this.cloudDbUrl = this.getStoredDbUrl();
    this.bus = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(LOCAL_BUS_NAME) : null;
    this.eventSource = null;
    this.listeners = [];
    this.pollTimer = null;
    this.isConnected = false;

    if (this.bus) {
      this.bus.onmessage = (e) => {
        if (e.data) this.notifyListeners(e.data);
      };
    }
  }

  getStoredDbUrl() {
    try {
      const stored = localStorage.getItem("hawassa_cloud_db_url");
      if (stored && stored.startsWith("http")) return stored.trim().replace(/\/$/, "");
    } catch (e) {}
    return DEFAULT_FIREBASE_RTDB_URL;
  }

  setCloudDbUrl(newUrl) {
    if (!newUrl) return;
    const clean = newUrl.trim().replace(/\/$/, "");
    this.cloudDbUrl = clean;
    try {
      localStorage.setItem("hawassa_cloud_db_url", clean);
    } catch (e) {}
    this.restartListener();
  }

  onEvent(callback) {
    this.listeners.push(callback);
  }

  notifyListeners(data) {
    this.listeners.forEach((cb) => {
      try { cb(data); } catch (e) {}
    });
  }

  broadcast(eventData) {
    if (this.bus) {
      try { this.bus.postMessage(eventData); } catch (e) {}
    }
    this.notifyListeners(eventData);
  }

  // 1. Announce Permitted Vehicle Category
  async announcePermittedCategory(stationId, category) {
    const payload = { activePermittedCategory: category };
    const eventData = {
      type: "PERMITTED_CATEGORY_CHANGED",
      stationId,
      activePermittedCategory: category
    };
    this.broadcast(eventData);

    // Write to Firebase Realtime Database
    try {
      await fetch(`${this.cloudDbUrl}/stations/${stationId}.json`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
    } catch (e) {}

    // Also notify Vercel / local REST API if available
    try {
      await fetch(`/api/admin/station/${stationId}/permitted-category`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ category })
      });
    } catch (e) {}
  }

  // 2. Advance Queue (Call Next Driver)
  async callNextDriver(stationId, category, newServingNumber) {
    const eventData = {
      type: "QUEUE_ADVANCED",
      stationId,
      category,
      currentlyServing: newServingNumber
    };
    this.broadcast(eventData);

    // Update lane in Firebase Realtime Database
    try {
      await fetch(`${this.cloudDbUrl}/stations/${stationId}/lanes/${category}.json`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentlyServing: newServingNumber })
      });
    } catch (e) {}

    try {
      await fetch(`/api/admin/station/${stationId}/call-next`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ category })
      });
    } catch (e) {}
  }

  // 3. Broadcast 10-Minute Audible Warning Alert
  async broadcastTenMinAlert(stationId, category) {
    const eventData = {
      type: "TEN_MINUTE_ALERT_BROADCAST",
      stationId,
      category,
      timestamp: Date.now()
    };
    this.broadcast(eventData);

    try {
      await fetch(`${this.cloudDbUrl}/stations/${stationId}/broadcastAlert.json`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ category, timestamp: Date.now() })
      });
    } catch (e) {}

    try {
      await fetch(`/api/admin/station/${stationId}/broadcast-alert`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ category })
      });
    } catch (e) {}
  }

  // 4. Update Fuel Stock (Benzene / Diesel)
  async updateFuelStock(stationId, isBenzene, newStatus) {
    const eventData = {
      type: "FUEL_STATUS_UPDATED",
      stationId,
      isBenzene,
      status: newStatus
    };
    this.broadcast(eventData);

    const patchPayload = isBenzene ? { benzeneStatus: newStatus } : { dieselStatus: newStatus };
    try {
      await fetch(`${this.cloudDbUrl}/stations/${stationId}.json`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(patchPayload)
      });
    } catch (e) {}

    try {
      await fetch(`/api/admin/station/${stationId}/fuel-stock`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ isBenzene, status: newStatus })
      });
    } catch (e) {}
  }

  // 5. Connect to Real-time Stream & Start Fallback Polling
  initRealtimeStream(onStatusChange) {
    this.onStatusChange = onStatusChange;
    this.startSSE();
    this.startPolling();
  }

  startSSE() {
    if (this.eventSource) {
      try { this.eventSource.close(); } catch(e) {}
    }

    try {
      // Direct Firebase Realtime Database SSE stream
      const streamUrl = `${this.cloudDbUrl}/stations.json`;
      this.eventSource = new EventSource(streamUrl);

      this.eventSource.onopen = () => {
        this.isConnected = true;
        if (this.onStatusChange) this.onStatusChange(true, `Live Cloud DB Connected: ${this.cloudDbUrl}`);
      };

      this.eventSource.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          this.handleCloudStreamData(payload);
        } catch (e) {}
      };

      this.eventSource.onerror = () => {
        this.isConnected = false;
        if (this.onStatusChange) this.onStatusChange(false, "Cloud DB: Polling Fallback Active");
      };
    } catch (e) {
      if (this.onStatusChange) this.onStatusChange(false, "Cloud DB: Polling Mode");
    }
  }

  handleCloudStreamData(payload) {
    if (!payload) return;
    // Firebase returns { path: "/...", data: ... }
    if (payload.path && payload.data) {
      const parts = payload.path.replace(/^\//, '').split('/');
      if (parts.length >= 1 && parts[0]) {
        const stationId = parts[0];
        if (parts.length === 1 && typeof payload.data === 'object') {
          // Full station object update
          this.broadcast({
            type: "STATION_FULL_UPDATE",
            stationId,
            stationData: payload.data
          });
        } else if (parts[1] === "activePermittedCategory") {
          this.broadcast({
            type: "PERMITTED_CATEGORY_CHANGED",
            stationId,
            activePermittedCategory: payload.data
          });
        } else if (parts[1] === "lanes" && parts[2] && payload.data.currentlyServing !== undefined) {
          this.broadcast({
            type: "QUEUE_ADVANCED",
            stationId,
            category: parts[2],
            currentlyServing: payload.data.currentlyServing
          });
        } else if (parts[1] === "broadcastAlert") {
          this.broadcast({
            type: "TEN_MINUTE_ALERT_BROADCAST",
            stationId,
            category: payload.data.category || "ALL"
          });
        } else if (parts[1] === "benzeneStatus" || parts[1] === "dieselStatus") {
          this.broadcast({
            type: "FUEL_STATUS_UPDATED",
            stationId,
            isBenzene: parts[1] === "benzeneStatus",
            status: payload.data
          });
        }
      }
    } else if (typeof payload === 'object') {
      // Initial bulk station dump
      this.broadcast({
        type: "INITIAL_CLOUD_DUMP",
        data: payload
      });
    }
  }

  startPolling() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.pollTimer = setInterval(async () => {
      try {
        const res = await fetch(`${this.cloudDbUrl}/stations.json`, { cache: 'no-store' });
        if (res.ok) {
          const json = await res.json();
          if (json && typeof json === 'object') {
            this.handleCloudStreamData(json);
            if (!this.isConnected && this.onStatusChange) {
              this.onStatusChange(true, "Cloud DB: Connected via HTTP Poll");
            }
          }
        }
      } catch (e) {}
    }, 4000);
  }

  restartListener() {
    this.startSSE();
  }

  // Push Initial Seed Data to Cloud Database
  async seedInitialStations(initialData) {
    try {
      const formatted = {};
      initialData.forEach((st) => { formatted[st.id] = st; });
      const res = await fetch(`${this.cloudDbUrl}/stations.json`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(formatted)
      });
      return res.ok;
    } catch (e) {
      return false;
    }
  }
}

// Global Singleton Export
window.HawassaCloudSync = new HawassaCloudSyncClient();
