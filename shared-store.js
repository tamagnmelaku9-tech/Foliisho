/**
 * Hawassa Transit Centralized Shared State Store
 * Links Web Admin Dashboard, Mobile Driver App, and Android APK to a single shared data source.
 * Persists immediately to localStorage and synchronizes across the network via /api/stations.
 */

(function(window) {
  const STORAGE_KEY = "HAWASSA_SHARED_STATIONS_STATE";
  const BROADCAST_CHANNEL_NAME = "hawassa_queue_bus";

  // Initial Hawassa Stations Data Source
  const DEFAULT_STATIONS = [
    {
      id: "benzene_madya",
      nameEn: "Benzene Madya Central",
      nameAm: "ቤንዚን ማድያ ዋና ጣቢያ",
      neighborhoodEn: "Piazza / Menahariya Road",
      neighborhoodAm: "ፒያሳ ወደ መናኸሪያ መታጠፊያ",
      distanceKm: 1.2,
      benzeneStatus: "AVAILABLE",
      dieselStatus: "AVAILABLE",
      congestion: "MODERATE",
      congestionLevel: "MODERATE",
      activePermittedCategory: "BAJAJ",
      lanes: {
        BAJAJ: { currentlyServing: 118, lastAssigned: 126, avgSec: 110, avgDurationSeconds: 110, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 62, lastAssigned: 68, avgSec: 240, avgDurationSeconds: 240, isActive: true },
        PUBLIC_BUS: { currentlyServing: 21, lastAssigned: 24, avgSec: 480, avgDurationSeconds: 480, isActive: true },
        CAR: { currentlyServing: 39, lastAssigned: 45, avgSec: 180, avgDurationSeconds: 180, isActive: true },
        MOTORCYCLE: { currentlyServing: 16, lastAssigned: 18, avgSec: 90, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 10, lastAssigned: 12, avgSec: 800, avgDurationSeconds: 800, isActive: true }
      }
    },
    {
      id: "total_menahariya",
      nameEn: "TotalEnergies Menahariya",
      nameAm: "ቶታል ኢነርጂ መናኸሪያ",
      neighborhoodEn: "Main Bus Terminal",
      neighborhoodAm: "ሀዋሳ አውቶቡስ መናኸሪያ",
      distanceKm: 2.1,
      benzeneStatus: "AVAILABLE",
      dieselStatus: "AVAILABLE",
      congestion: "HEAVY",
      congestionLevel: "HEAVY",
      activePermittedCategory: "TAXI_MINIBUS",
      lanes: {
        BAJAJ: { currentlyServing: 138, lastAssigned: 160, avgSec: 110, avgDurationSeconds: 110, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 81, lastAssigned: 95, avgSec: 220, avgDurationSeconds: 220, isActive: true },
        PUBLIC_BUS: { currentlyServing: 27, lastAssigned: 35, avgSec: 500, avgDurationSeconds: 500, isActive: true },
        CAR: { currentlyServing: 42, lastAssigned: 52, avgSec: 190, avgDurationSeconds: 190, isActive: true },
        MOTORCYCLE: { currentlyServing: 19, lastAssigned: 22, avgSec: 90, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 14, lastAssigned: 18, avgSec: 850, avgDurationSeconds: 850, isActive: true }
      }
    },
    {
      id: "noc_piassa",
      nameEn: "NOC Piassa Central",
      nameAm: "ኤን ኦ ሲ ፒያሳ መሀል ከተማ",
      neighborhoodEn: "Piassa Commercial Center",
      neighborhoodAm: "ፒያሳ የንግድ ማዕከል",
      distanceKm: 0.8,
      benzeneStatus: "AVAILABLE",
      dieselStatus: "LOW_STOCK",
      congestion: "LOW",
      congestionLevel: "LOW",
      activePermittedCategory: "BAJAJ",
      lanes: {
        BAJAJ: { currentlyServing: 82, lastAssigned: 88, avgSec: 100, avgDurationSeconds: 100, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 28, lastAssigned: 32, avgSec: 240, avgDurationSeconds: 240, isActive: true },
        PUBLIC_BUS: { currentlyServing: 9, lastAssigned: 10, avgSec: 540, avgDurationSeconds: 540, isActive: true },
        CAR: { currentlyServing: 27, lastAssigned: 30, avgSec: 180, avgDurationSeconds: 180, isActive: true },
        MOTORCYCLE: { currentlyServing: 14, lastAssigned: 15, avgSec: 90, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 5, lastAssigned: 6, avgSec: 900, avgDurationSeconds: 900, isActive: true }
      }
    },
    {
      id: "ola_datto",
      nameEn: "Ola Energy Datto",
      nameAm: "ኦላ ኢነርጂ ዳቶ",
      neighborhoodEn: "Datto Industrial Corridor",
      neighborhoodAm: "ዳቶ ኢንዱስትሪ ኮሪደር",
      distanceKm: 2.8,
      benzeneStatus: "OUT_OF_STOCK",
      dieselStatus: "AVAILABLE",
      congestion: "LOW",
      congestionLevel: "LOW",
      activePermittedCategory: "TRUCK_HEAVY",
      lanes: {
        BAJAJ: { currentlyServing: 45, lastAssigned: 45, avgSec: 110, avgDurationSeconds: 110, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 30, lastAssigned: 30, avgSec: 240, avgDurationSeconds: 240, isActive: true },
        PUBLIC_BUS: { currentlyServing: 12, lastAssigned: 12, avgSec: 480, avgDurationSeconds: 480, isActive: true },
        CAR: { currentlyServing: 18, lastAssigned: 18, avgSec: 180, avgDurationSeconds: 180, isActive: true },
        MOTORCYCLE: { currentlyServing: 8, lastAssigned: 8, avgSec: 90, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 11, lastAssigned: 14, avgSec: 850, avgDurationSeconds: 850, isActive: true }
      }
    },
    {
      id: "tabor_oil",
      nameEn: "National Oil Tabor",
      nameAm: "ናሽናል ኦይል ታቦር",
      neighborhoodEn: "Mount Tabor Transit",
      neighborhoodAm: "ታቦር ተራራ መታጠፊያ",
      distanceKm: 3.3,
      benzeneStatus: "AVAILABLE",
      dieselStatus: "AVAILABLE",
      congestion: "MODERATE",
      congestionLevel: "MODERATE",
      activePermittedCategory: "CAR",
      lanes: {
        BAJAJ: { currentlyServing: 68, lastAssigned: 75, avgSec: 110, avgDurationSeconds: 110, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 44, lastAssigned: 50, avgSec: 230, avgDurationSeconds: 230, isActive: true },
        PUBLIC_BUS: { currentlyServing: 15, lastAssigned: 18, avgSec: 510, avgDurationSeconds: 510, isActive: true },
        CAR: { currentlyServing: 30, lastAssigned: 35, avgSec: 190, avgDurationSeconds: 190, isActive: true },
        MOTORCYCLE: { currentlyServing: 11, lastAssigned: 12, avgSec: 90, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 8, lastAssigned: 10, avgSec: 850, avgDurationSeconds: 850, isActive: true }
      }
    }
  ];

  class HawassaSharedStoreManager {
    constructor() {
      this.listeners = [];
      this.channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(BROADCAST_CHANNEL_NAME) : null;
      this.stations = this.loadInitialStations();

      // Listen for updates from other tabs/windows
      if (this.channel) {
        this.channel.onmessage = (e) => {
          if (e.data && e.data.type === "SHARED_STORE_SYNC") {
            this.stations = e.data.stations;
            this.notify(this.stations);
          }
        };
      }

      // Listen to window storage events for cross-tab sync
      if (typeof window !== 'undefined') {
        window.addEventListener("storage", (e) => {
          if (e.key === STORAGE_KEY && e.newValue) {
            try {
              this.stations = JSON.parse(e.newValue);
              this.notify(this.stations);
            } catch (err) {}
          }
        });
      }

      // Poll network API every 2 seconds to keep in sync with Android and cloud
      this.startNetworkPolling();
    }

    loadInitialStations() {
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored);
          if (Array.isArray(parsed) && parsed.length > 0) return parsed;
        }
      } catch (e) {}
      this.saveLocal(DEFAULT_STATIONS);
      return DEFAULT_STATIONS;
    }

    saveLocal(newStations) {
      this.stations = newStations;
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(newStations));
      } catch (e) {}
      if (this.channel) {
        try {
          this.channel.postMessage({ type: "SHARED_STORE_SYNC", stations: newStations });
        } catch (e) {}
      }
      this.notify(newStations);
    }

    notify(stations) {
      this.listeners.forEach((cb) => {
        try { cb(stations); } catch (e) {}
      });
      // Also dispatch standard custom DOM event
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent("hawassa_shared_update", { detail: stations }));
      }
    }

    subscribe(callback) {
      this.listeners.push(callback);
      // Immediately invoke with current state
      callback(this.stations);
      return () => {
        this.listeners = this.listeners.filter(cb => cb !== callback);
      };
    }

    getStations() {
      return this.stations;
    }

    getStation(id) {
      return this.stations.find(s => s.id === id) || this.stations[0];
    }

    // 1. UPDATE PERMITTED VEHICLE CATEGORY
    setPermittedCategory(stationId, category) {
      const updated = this.stations.map(st => {
        if (st.id === stationId) {
          return { ...st, activePermittedCategory: category };
        }
        return st;
      });
      this.saveLocal(updated);
      this.syncToBackend({
        stationId,
        activePermittedCategory: category
      });
    }

    // 2. ADVANCE QUEUE (CALL NEXT DRIVER)
    advanceQueue(stationId, category) {
      let newServing = 0;
      const updated = this.stations.map(st => {
        if (st.id === stationId) {
          const lane = st.lanes[category] || { currentlyServing: 100, lastAssigned: 105, avgSec: 120 };
          const updatedServing = (lane.currentlyServing < lane.lastAssigned)
            ? lane.currentlyServing + 1
            : lane.lastAssigned + 1;
          newServing = updatedServing;
          return {
            ...st,
            lanes: {
              ...st.lanes,
              [category]: {
                ...lane,
                currentlyServing: updatedServing,
                lastAssigned: Math.max(lane.lastAssigned, updatedServing)
              }
            }
          };
        }
        return st;
      });

      this.saveLocal(updated);
      this.syncToBackend({
        stationId,
        category,
        currentlyServing: newServing
      });
      return newServing;
    }

    // 3. JOIN QUEUE
    joinQueue(stationId, category) {
      let newAssigned = 0;
      const updated = this.stations.map(st => {
        if (st.id === stationId) {
          const lane = st.lanes[category] || { currentlyServing: 100, lastAssigned: 100, avgSec: 120 };
          newAssigned = lane.lastAssigned + 1;
          return {
            ...st,
            lanes: {
              ...st.lanes,
              [category]: {
                ...lane,
                lastAssigned: newAssigned
              }
            }
          };
        }
        return st;
      });

      this.saveLocal(updated);
      return newAssigned;
    }

    // 4. UPDATE FUEL STATUS
    updateFuelStatus(stationId, isBenzene, newStatus) {
      const updated = this.stations.map(st => {
        if (st.id === stationId) {
          return isBenzene ? { ...st, benzeneStatus: newStatus } : { ...st, dieselStatus: newStatus };
        }
        return st;
      });
      this.saveLocal(updated);
      this.syncToBackend({
        stationId,
        [isBenzene ? "benzeneStatus" : "dieselStatus"]: newStatus
      });
    }

    // Sync to backend /api
    async syncToBackend(payload) {
      try {
        await fetch("/api/stations/update", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
      } catch (e) {}
    }

    // Polling backend every 2 seconds
    startNetworkPolling() {
      setInterval(async () => {
        try {
          const res = await fetch("/api/stations", { cache: "no-store" });
          if (res.ok) {
            const json = await res.json();
            if (json.success && Array.isArray(json.data) && json.data.length > 0) {
              // Check if data is newer or different
              const jsonStr = JSON.stringify(json.data);
              const currentStr = JSON.stringify(this.stations);
              if (jsonStr !== currentStr) {
                this.stations = json.data;
                try {
                  localStorage.setItem(STORAGE_KEY, jsonStr);
                } catch(e) {}
                this.notify(this.stations);
              }
            }
          }
        } catch (e) {}
      }, 2000);
    }
  }

  // Export singleton instance
  window.HawassaSharedStore = new HawassaSharedStoreManager();
})(window);
