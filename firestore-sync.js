/**
 * Hawassa Transit & Fuel Queue - Unified Cloud Firestore Sync Client
 * Connects Web Coordinator Dashboard, Mobile Driver Web App, and Android App
 * to a single shared Cloud Firestore database in Firebase project "foliisho".
 */

(function (window) {
  const FIREBASE_CONFIG = {
    apiKey: "AIzaSyBp_QKLNhTiVLKhDbcWniRPnkvJwEQ8H8Y",
    authDomain: "foliisho.firebaseapp.com",
    projectId: "foliisho",
    storageBucket: "foliisho.firebasestorage.app",
    messagingSenderId: "627677419836"
  };

  const ACTIVE_TICKET_STORAGE_KEY = "hawassa_active_ticket_id";
  const CACHED_STATIONS_KEY = "HAWASSA_SHARED_STATIONS_STATE";

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
      congestionLevel: "MODERATE",
      isOpen: true,
      activePermittedCategory: "BAJAJ",
      lanes: {
        BAJAJ: { currentlyServing: 118, lastAssigned: 126, avgDurationSeconds: 110, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 62, lastAssigned: 68, avgDurationSeconds: 240, isActive: true },
        PUBLIC_BUS: { currentlyServing: 21, lastAssigned: 24, avgDurationSeconds: 480, isActive: true },
        CAR: { currentlyServing: 39, lastAssigned: 45, avgDurationSeconds: 180, isActive: true },
        MOTORCYCLE: { currentlyServing: 16, lastAssigned: 18, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 10, lastAssigned: 12, avgDurationSeconds: 800, isActive: true }
      }
    },
    {
      id: "total_menahariya",
      nameEn: "TotalEnergies Menahariya",
      nameAm: "ቶታል ኢነርጂ መናኸሪያ",
      neighborhoodEn: "Hawassa Main Bus Terminal",
      neighborhoodAm: "ሀዋሳ አውቶቡስ መናኸሪያ",
      distanceKm: 2.4,
      benzeneStatus: "AVAILABLE",
      dieselStatus: "AVAILABLE",
      congestionLevel: "HEAVY",
      isOpen: true,
      activePermittedCategory: "TAXI_MINIBUS",
      lanes: {
        BAJAJ: { currentlyServing: 138, lastAssigned: 160, avgDurationSeconds: 110, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 81, lastAssigned: 95, avgDurationSeconds: 220, isActive: true },
        PUBLIC_BUS: { currentlyServing: 27, lastAssigned: 35, avgDurationSeconds: 500, isActive: true },
        CAR: { currentlyServing: 42, lastAssigned: 52, avgDurationSeconds: 190, isActive: true },
        MOTORCYCLE: { currentlyServing: 19, lastAssigned: 22, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 14, lastAssigned: 18, avgDurationSeconds: 850, isActive: true }
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
      congestionLevel: "LOW",
      isOpen: true,
      activePermittedCategory: "BAJAJ",
      lanes: {
        BAJAJ: { currentlyServing: 82, lastAssigned: 88, avgDurationSeconds: 100, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 28, lastAssigned: 32, avgDurationSeconds: 240, isActive: true },
        PUBLIC_BUS: { currentlyServing: 9, lastAssigned: 10, avgDurationSeconds: 540, isActive: true },
        CAR: { currentlyServing: 27, lastAssigned: 30, avgDurationSeconds: 180, isActive: true },
        MOTORCYCLE: { currentlyServing: 14, lastAssigned: 15, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 5, lastAssigned: 6, avgDurationSeconds: 900, isActive: true }
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
      congestionLevel: "LOW",
      isOpen: true,
      activePermittedCategory: "TRUCK_HEAVY",
      lanes: {
        BAJAJ: { currentlyServing: 45, lastAssigned: 45, avgDurationSeconds: 110, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 30, lastAssigned: 30, avgDurationSeconds: 240, isActive: true },
        PUBLIC_BUS: { currentlyServing: 12, lastAssigned: 12, avgDurationSeconds: 480, isActive: true },
        CAR: { currentlyServing: 18, lastAssigned: 18, avgDurationSeconds: 180, isActive: true },
        MOTORCYCLE: { currentlyServing: 8, lastAssigned: 8, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 11, lastAssigned: 14, avgDurationSeconds: 850, isActive: true }
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
      congestionLevel: "MODERATE",
      isOpen: true,
      activePermittedCategory: "CAR",
      lanes: {
        BAJAJ: { currentlyServing: 68, lastAssigned: 75, avgDurationSeconds: 110, isActive: true },
        TAXI_MINIBUS: { currentlyServing: 44, lastAssigned: 50, avgDurationSeconds: 230, isActive: true },
        PUBLIC_BUS: { currentlyServing: 15, lastAssigned: 18, avgDurationSeconds: 510, isActive: true },
        CAR: { currentlyServing: 30, lastAssigned: 35, avgDurationSeconds: 190, isActive: true },
        MOTORCYCLE: { currentlyServing: 11, lastAssigned: 12, avgDurationSeconds: 90, isActive: true },
        TRUCK_HEAVY: { currentlyServing: 8, lastAssigned: 10, avgDurationSeconds: 850, isActive: true }
      }
    }
  ];

  const STATION_ORDER = ["benzene_madya", "total_menahariya", "noc_piassa", "ola_datto", "tabor_oil"];

  class HawassaFirestoreClient {
    constructor() {
      this.db = null;
      this.stations = this._loadCachedStations();
      this.tickets = [];
      this.stationListeners = [];
      this.ticketListeners = [];
      this.statusListeners = [];
      this.alertListeners = [];
      this.isConnected = false;
      this.statusMessage = "Connecting to Firestore (foliisho)...";
      this._unsubStations = null;
      this._unsubTickets = null;
      this._unsubMyTicket = null;
      this._lastBroadcastSeen = Date.now();
      this._initialized = false;
    }

    _loadCachedStations() {
      try {
        const raw = localStorage.getItem(CACHED_STATIONS_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed) && parsed.length > 0) return parsed;
        }
      } catch (e) {}
      return JSON.parse(JSON.stringify(DEFAULT_STATIONS));
    }

    _saveCachedStations(list) {
      try {
        localStorage.setItem(CACHED_STATIONS_KEY, JSON.stringify(list));
      } catch (e) {}
    }

    _emitStatus(connected, msg) {
      this.isConnected = connected;
      this.statusMessage = msg;
      this.statusListeners.forEach((cb) => {
        try { cb(connected, msg); } catch (e) {}
      });
    }

    _emitStations() {
      this.stationListeners.forEach((cb) => {
        try { cb(this.stations); } catch (e) {}
      });
    }

    _emitTickets() {
      this.ticketListeners.forEach((cb) => {
        try { cb(this.tickets); } catch (e) {}
      });
    }

    onStationsChange(cb) {
      this.stationListeners.push(cb);
      cb(this.stations);
      return () => {
        this.stationListeners = this.stationListeners.filter((fn) => fn !== cb);
      };
    }

    onTicketsChange(cb) {
      this.ticketListeners.push(cb);
      cb(this.tickets);
      return () => {
        this.ticketListeners = this.ticketListeners.filter((fn) => fn !== cb);
      };
    }

    onStatusChange(cb) {
      this.statusListeners.push(cb);
      cb(this.isConnected, this.statusMessage);
      return () => {
        this.statusListeners = this.statusListeners.filter((fn) => fn !== cb);
      };
    }

    onBroadcastAlert(cb) {
      this.alertListeners.push(cb);
    }

    getStations() {
      return this.stations;
    }

    _getBaselineStation(stationId) {
      const current = this.stations.find((s) => s.id === stationId);
      if (current) return JSON.parse(JSON.stringify(current));
      const def = DEFAULT_STATIONS.find((s) => s.id === stationId) || DEFAULT_STATIONS[0];
      return JSON.parse(JSON.stringify(def));
    }

    _updateLocalStation(stationId, mutatorFn) {
      let updated = false;
      this.stations = this.stations.map((s) => {
        if (s.id !== stationId) return s;
        updated = true;
        const copy = JSON.parse(JSON.stringify(s));
        mutatorFn(copy);
        return copy;
      });
      if (!updated) {
        const base = this._getBaselineStation(stationId);
        mutatorFn(base);
        this.stations.push(base);
      }
      this._saveCachedStations(this.stations);
      this._emitStations();
    }

    getTickets(stationId) {
      if (!stationId) return this.tickets;
      return this.tickets.filter((t) => t.stationId === stationId);
    }

    async init() {
      if (this._initialized) return;
      this._initialized = true;

      if (typeof firebase === "undefined" || !firebase.firestore) {
        this._emitStatus(false, "Firebase Firestore SDK failed to load");
        return;
      }

      try {
        if (!firebase.apps || firebase.apps.length === 0) {
          firebase.initializeApp(FIREBASE_CONFIG);
        }
        this.db = firebase.firestore();
        try {
          await Promise.race([
            this.db.enablePersistence({ synchronizeTabs: true }),
            new Promise((resolve) => setTimeout(resolve, 1000))
          ]);
        } catch (persistErr) {
          // Ignore failed-precondition (multi-tab without sync) or unimplemented (private browsing)
        }
      } catch (err) {
        console.error("Firestore init error:", err);
        this._emitStatus(false, "Firestore Init Error: " + (err.message || err));
        return;
      }

      this._emitStatus(false, "Syncing Cloud Firestore (foliisho)...");

      // 1. Seed any missing default stations
      try {
        const snap = await this.db.collection("stations").get();
        const existingIds = new Set();
        snap.forEach((d) => existingIds.add(d.id));
        const missing = DEFAULT_STATIONS.filter((st) => !existingIds.has(st.id));
        if (missing.length > 0) {
          await this.seedStationsList(missing);
        }
      } catch (err) {
        console.warn("Firestore station check/seed warning:", err);
        if (err && err.code === "permission-denied") {
          this._emitStatus(false, "Firestore Permission Denied - Publish Rules");
        }
      }

      // 2. Real-time stations listener
      this._unsubStations = this.db.collection("stations").onSnapshot(
        (snapshot) => {
          const loaded = [];
          snapshot.forEach((doc) => {
            const d = doc.data();
            if (d) {
              loaded.push({ ...d, id: d.id || doc.id });
              // Detect fresh coordinator 10-min alert broadcasts
              if (d.lastBroadcastAlert && d.lastBroadcastAlert.timestamp > this._lastBroadcastSeen) {
                this._lastBroadcastSeen = d.lastBroadcastAlert.timestamp;
                this.alertListeners.forEach((cb) => {
                  try { cb({ stationId: d.id || doc.id, ...d.lastBroadcastAlert }); } catch (e) {}
                });
              }
            }
          });

          if (loaded.length > 0) {
            loaded.sort((a, b) => {
              const ia = STATION_ORDER.indexOf(a.id);
              const ib = STATION_ORDER.indexOf(b.id);
              return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
            });
            this.stations = loaded;
            this._saveCachedStations(loaded);
            this._emitStations();
          }
          this._emitStatus(true, "Firestore Live: foliisho");
        },
        (err) => {
          console.error("Firestore stations listener error:", err);
          if (err && err.code === "permission-denied") {
            this._emitStatus(false, "Firestore Permission Denied - Check Rules");
          } else {
            this._emitStatus(false, "Firestore Reconnecting...");
          }
        }
      );

      // 3. Real-time active queue tickets listener
      this._unsubTickets = this.db
        .collection("tickets")
        .where("status", "in", ["WAITING", "CALLED", "SERVING"])
        .onSnapshot(
          (snapshot) => {
            const list = [];
            snapshot.forEach((doc) => {
              const d = doc.data();
              if (d) list.push({ ...d, ticketId: d.ticketId || doc.id });
            });
            list.sort((a, b) => (a.ticketNumber || 0) - (b.ticketNumber || 0));
            this.tickets = list;
            this._emitTickets();
          },
          (err) => {
            console.error("Firestore tickets listener error:", err);
          }
        );
    }

    async seedInitialStations() {
      await this.seedStationsList(DEFAULT_STATIONS);
    }

    async seedStationsList(stationsList) {
      if (!this.db || !Array.isArray(stationsList) || stationsList.length === 0) return;
      const batch = this.db.batch();
      const ts = firebase.firestore.FieldValue.serverTimestamp();
      stationsList.forEach((st) => {
        const ref = this.db.collection("stations").doc(st.id);
        batch.set(ref, { ...st, updatedAt: ts }, { merge: true });
      });
      await batch.commit();
    }

    // ====================================================
    // DRIVER OPERATIONS (Write directly to Firestore)
    // ====================================================

    async joinQueue({ stationId, driverPhone, licensePlate, driverName, vehicleCategory }) {
      await this.init();
      if (!this.db) throw new Error("Cloud Firestore is not initialized.");

      const plateClean = String(licensePlate || "").trim().toUpperCase();
      const phoneClean = String(driverPhone || "").trim();
      const nameClean = String(driverName || "").trim() || "Driver";

      if (plateClean.length < 4) {
        throw new Error("Please enter a valid license plate (e.g. AA-2-84920).");
      }
      if (phoneClean.length < 9) {
        throw new Error("Please enter a valid Ethiopian phone number (e.g. 0916123456).");
      }

      // Check for duplicate active plate across Firestore
      const existingSnap = await this.db
        .collection("tickets")
        .where("licensePlate", "==", plateClean)
        .get();

      let duplicateTicket = null;
      existingSnap.forEach((doc) => {
        const data = doc.data();
        if (data && (data.status === "WAITING" || data.status === "CALLED" || data.status === "SERVING")) {
          duplicateTicket = data;
        }
      });
      if (duplicateTicket) {
        throw new Error(`Vehicle ${plateClean} already has active ticket #${duplicateTicket.ticketNumber} at ${duplicateTicket.stationNameEn || duplicateTicket.stationId}.`);
      }

      const stationRef = this.db.collection("stations").doc(stationId);
      const ticketId = `TKT-${Date.now()}-${Math.floor(Math.random() * 900 + 100)}`;
      const ticketRef = this.db.collection("tickets").doc(ticketId);
      const ts = firebase.firestore.FieldValue.serverTimestamp();

      const createdTicket = await this.db.runTransaction(async (transaction) => {
        const stDoc = await transaction.get(stationRef);
        let stationData;
        if (!stDoc.exists) {
          const fallback = DEFAULT_STATIONS.find((s) => s.id === stationId) || DEFAULT_STATIONS[0];
          stationData = JSON.parse(JSON.stringify(fallback));
        } else {
          stationData = JSON.parse(JSON.stringify(stDoc.data()));
        }

        const cat = vehicleCategory || stationData.activePermittedCategory || "BAJAJ";
        const lanes = stationData.lanes || {};
        const lane = lanes[cat] || { currentlyServing: 100, lastAssigned: 100, avgDurationSeconds: 120, isActive: true };
        if (lane.isActive === false) {
          throw new Error(`The ${cat} lane at ${stationData.nameEn} is currently paused by the coordinator.`);
        }

        const newNumber = (lane.lastAssigned || 100) + 1;
        lane.lastAssigned = newNumber;
        lanes[cat] = lane;

        transaction.set(
          stationRef,
          {
            ...stationData,
            id: stationId,
            lanes,
            updatedAt: ts
          },
          { merge: true }
        );

        const ticketObj = {
          ticketId,
          stationId,
          stationNameEn: stationData.nameEn,
          stationNameAm: stationData.nameAm,
          vehicleCategory: cat,
          driverName: nameClean,
          phoneNumber: phoneClean,
          licensePlate: plateClean,
          ticketNumber: newNumber,
          status: "WAITING",
          calledPumpNumber: null,
          createdAt: ts,
          updatedAt: ts
        };

        transaction.set(ticketRef, ticketObj);
        return ticketObj;
      });

      this.saveActiveTicketId(ticketId);
      return createdTicket;
    }

    async leaveQueue(ticketId) {
      await this.init();
      this.clearSavedTicketId();
      if (this._unsubMyTicket) {
        try { this._unsubMyTicket(); } catch (e) {}
        this._unsubMyTicket = null;
      }
      if (!this.db || !ticketId) return;
      try {
        await this.db.collection("tickets").doc(ticketId).update({
          status: "CANCELLED",
          updatedAt: firebase.firestore.FieldValue.serverTimestamp()
        });
      } catch (e) {
        console.warn("leaveQueue update warning:", e);
      }
    }

    saveActiveTicketId(ticketId) {
      try { localStorage.setItem(ACTIVE_TICKET_STORAGE_KEY, ticketId); } catch (e) {}
    }

    getSavedTicketId() {
      try { return localStorage.getItem(ACTIVE_TICKET_STORAGE_KEY); } catch (e) { return null; }
    }

    clearSavedTicketId() {
      try { localStorage.removeItem(ACTIVE_TICKET_STORAGE_KEY); } catch (e) {}
    }

    subscribeToTicket(ticketId, callback) {
      if (this._unsubMyTicket) {
        try { this._unsubMyTicket(); } catch (e) {}
        this._unsubMyTicket = null;
      }
      if (!this.db || !ticketId) return () => {};

      this._unsubMyTicket = this.db.collection("tickets").doc(ticketId).onSnapshot(
        (doc) => {
          if (doc.exists) {
            const data = doc.data();
            callback({ ...data, ticketId: data.ticketId || doc.id });
          } else {
            callback(null);
          }
        },
        (err) => {
          console.error("Driver ticket listener error:", err);
        }
      );
      return this._unsubMyTicket;
    }

    // ====================================================
    // COORDINATOR OPERATIONS (Write directly to Firestore)
    // ====================================================

    async setPermittedCategory(stationId, category) {
      this._updateLocalStation(stationId, (st) => {
        st.activePermittedCategory = category;
      });
      await this.init();
      if (!this.db) return;
      const base = this._getBaselineStation(stationId);
      base.activePermittedCategory = category;
      await this.db.collection("stations").doc(stationId).set(
        {
          ...base,
          id: stationId,
          activePermittedCategory: category,
          updatedAt: firebase.firestore.FieldValue.serverTimestamp()
        },
        { merge: true }
      );
    }

    async callNextDriver(stationId, category) {
      await this.init();
      if (!this.db) return 0;

      const stationRef = this.db.collection("stations").doc(stationId);
      const ts = firebase.firestore.FieldValue.serverTimestamp();

      const nextServing = await this.db.runTransaction(async (transaction) => {
        const stDoc = await transaction.get(stationRef);
        const stData = stDoc.exists
          ? JSON.parse(JSON.stringify(stDoc.data()))
          : this._getBaselineStation(stationId);
        const lanes = stData.lanes || {};
        const lane = lanes[category] || { currentlyServing: 100, lastAssigned: 100, avgDurationSeconds: 120, isActive: true };

        const updatedServing = (lane.currentlyServing < lane.lastAssigned)
          ? lane.currentlyServing + 1
          : lane.lastAssigned + 1;

        lane.currentlyServing = updatedServing;
        lane.lastAssigned = Math.max(lane.lastAssigned || 0, updatedServing);
        lanes[category] = lane;

        transaction.set(
          stationRef,
          {
            ...stData,
            id: stationId,
            lanes,
            updatedAt: ts
          },
          { merge: true }
        );
        return updatedServing;
      });

      this._updateLocalStation(stationId, (st) => {
        if (!st.lanes) st.lanes = {};
        const lane = st.lanes[category] || { currentlyServing: 100, lastAssigned: 100, avgDurationSeconds: 120, isActive: true };
        lane.currentlyServing = nextServing;
        lane.lastAssigned = Math.max(lane.lastAssigned || 0, nextServing);
        st.lanes[category] = lane;
      });

      // Sync matching tickets for this station & category
      try {
        const matchingSnap = await this.db
          .collection("tickets")
          .where("stationId", "==", stationId)
          .get();

        const batch = this.db.batch();
        const pumpBay = Math.floor(Math.random() * 4) + 1;
        let hasBatchWrites = false;

        matchingSnap.forEach((doc) => {
          const t = doc.data();
          if (t.vehicleCategory !== category) return;
          if (t.status !== "WAITING" && t.status !== "CALLED" && t.status !== "SERVING") return;

          if (t.ticketNumber === nextServing) {
            batch.update(doc.ref, { status: "CALLED", calledPumpNumber: pumpBay, updatedAt: ts });
            hasBatchWrites = true;
          } else if (t.ticketNumber < nextServing) {
            batch.update(doc.ref, { status: "COMPLETED", updatedAt: ts });
            hasBatchWrites = true;
          }
        });

        if (hasBatchWrites) await batch.commit();
      } catch (e) {
        console.warn("Ticket status sync during callNext warning:", e);
      }

      return nextServing;
    }

    async callSpecificTicket(ticket) {
      await this.init();
      if (!this.db || !ticket) return;

      const ts = firebase.firestore.FieldValue.serverTimestamp();
      const pumpBay = Math.floor(Math.random() * 4) + 1;
      const stationRef = this.db.collection("stations").doc(ticket.stationId);

      // Update station lane counter if needed
      await this.db.runTransaction(async (transaction) => {
        const stDoc = await transaction.get(stationRef);
        const stData = stDoc.exists
          ? JSON.parse(JSON.stringify(stDoc.data()))
          : this._getBaselineStation(ticket.stationId);
        const lanes = stData.lanes || {};
        const lane = lanes[ticket.vehicleCategory] || { currentlyServing: 0, lastAssigned: 0, avgDurationSeconds: 120, isActive: true };
        lanes[ticket.vehicleCategory] = {
          ...lane,
          currentlyServing: Math.max(lane.currentlyServing || 0, ticket.ticketNumber),
          lastAssigned: Math.max(lane.lastAssigned || 0, ticket.ticketNumber)
        };
        transaction.set(
          stationRef,
          {
            ...stData,
            id: ticket.stationId,
            lanes,
            updatedAt: ts
          },
          { merge: true }
        );
      });

      // Mark target ticket CALLED & earlier waiting/called tickets in same lane COMPLETED
      const snap = await this.db
        .collection("tickets")
        .where("stationId", "==", ticket.stationId)
        .get();

      const batch = this.db.batch();
      snap.forEach((doc) => {
        const t = doc.data();
        if (doc.id === ticket.ticketId || t.ticketId === ticket.ticketId) {
          batch.update(doc.ref, { status: "CALLED", calledPumpNumber: pumpBay, updatedAt: ts });
        } else if (
          t.vehicleCategory === ticket.vehicleCategory &&
          (t.status === "WAITING" || t.status === "CALLED") &&
          t.ticketNumber < ticket.ticketNumber
        ) {
          batch.update(doc.ref, { status: "COMPLETED", updatedAt: ts });
        }
      });
      await batch.commit();
    }

    async markTicketServed(ticketId) {
      await this.init();
      if (!this.db || !ticketId) return;
      await this.db.collection("tickets").doc(ticketId).update({
        status: "COMPLETED",
        updatedAt: firebase.firestore.FieldValue.serverTimestamp()
      });
    }

    async cancelTicket(ticketId) {
      await this.init();
      if (!this.db || !ticketId) return;
      await this.db.collection("tickets").doc(ticketId).update({
        status: "CANCELLED",
        updatedAt: firebase.firestore.FieldValue.serverTimestamp()
      });
    }

    async toggleLaneActive(stationId, category, isActive) {
      this._updateLocalStation(stationId, (st) => {
        if (!st.lanes) st.lanes = {};
        if (!st.lanes[category]) st.lanes[category] = { currentlyServing: 100, lastAssigned: 100, avgDurationSeconds: 120, isActive: true };
        st.lanes[category].isActive = isActive;
      });
      await this.init();
      if (!this.db) return;
      const base = this._getBaselineStation(stationId);
      if (!base.lanes) base.lanes = {};
      base.lanes[category] = { ...(base.lanes[category] || {}), isActive };
      await this.db.collection("stations").doc(stationId).set(
        {
          ...base,
          id: stationId,
          updatedAt: firebase.firestore.FieldValue.serverTimestamp()
        },
        { merge: true }
      );
    }

    async updateFuelStock(stationId, isBenzene, newStatus) {
      const field = isBenzene ? "benzeneStatus" : "dieselStatus";
      this._updateLocalStation(stationId, (st) => {
        st[field] = newStatus;
      });
      await this.init();
      if (!this.db) return;
      const base = this._getBaselineStation(stationId);
      base[field] = newStatus;
      await this.db.collection("stations").doc(stationId).set(
        {
          ...base,
          id: stationId,
          updatedAt: firebase.firestore.FieldValue.serverTimestamp()
        },
        { merge: true }
      );
    }

    async broadcastTenMinAlert(stationId, category) {
      await this.init();
      if (!this.db) return;
      const base = this._getBaselineStation(stationId);
      await this.db.collection("stations").doc(stationId).set(
        {
          ...base,
          id: stationId,
          lastBroadcastAlert: {
            category: category || "ALL",
            timestamp: Date.now()
          },
          updatedAt: firebase.firestore.FieldValue.serverTimestamp()
        },
        { merge: true }
      );
    }
  }

  window.HawassaFirestore = new HawassaFirestoreClient();
})(window);
