/**
 * Hawassa Transit & Fuel Queue Management System - Cloud REST API & Real-Time SSE Server
 * Multi-Station Architecture: Benzene Madya, Total Menahariya, NOC Piassa, Ola Datto, Tabor Oil
 */

const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const app = express();
app.use(cors({ origin: '*' }));
app.use(express.json());

// Serve PWA headers cleanly and serve static assets from /public and project root
app.use((req, res, next) => {
  if (req.path === '/sw.js') {
    res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    res.setHeader('Service-Worker-Allowed', '/');
  } else if (req.path.endsWith('manifest.json')) {
    res.setHeader('Content-Type', 'application/manifest+json; charset=utf-8');
    res.setHeader('Cache-Control', 'no-cache');
  }
  next();
});
app.use(express.static(path.join(__dirname, '..', 'public')));
app.use(express.static(path.join(__dirname, '..')));

// In-Memory & File-Backed Persistent Database
const STATE_FILE = path.join(__dirname, 'stations-state.json');
const stations = [
  {
    id: "benzene_madya",
    nameEn: "Benzene Madya Central",
    nameAm: "ቤንዚን ማድያ ዋና ጣቢያ",
    neighborhood: "Piazza / Menahariya Road",
    isOpen: true,
    benzeneStatus: "AVAILABLE",
    dieselStatus: "AVAILABLE",
    congestionLevel: "MODERATE",
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
    neighborhood: "Hawassa Main Bus Terminal",
    isOpen: true,
    benzeneStatus: "AVAILABLE",
    dieselStatus: "AVAILABLE",
    congestionLevel: "HEAVY",
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
    neighborhood: "Piassa Commercial Center",
    isOpen: true,
    benzeneStatus: "AVAILABLE",
    dieselStatus: "LOW_STOCK",
    congestionLevel: "LOW",
    activePermittedCategory: "BAJAJ",
    lanes: {
      BAJAJ: { currentlyServing: 82, lastAssigned: 88, avgDurationSeconds: 100, isActive: true },
      TAXI_MINIBUS: { currentlyServing: 28, lastAssigned: 32, avgDurationSeconds: 240, isActive: true },
      PUBLIC_BUS: { currentlyServing: 9, lastAssigned: 10, avgDurationSeconds: 540, isActive: true },
      CAR: { currentlyServing: 27, lastAssigned: 30, avgDurationSeconds: 180, isActive: true },
      MOTORCYCLE: { currentlyServing: 14, lastAssigned: 15, avgDurationSeconds: 90, isActive: true },
      TRUCK_HEAVY: { currentlyServing: 5, lastAssigned: 6, avgDurationSeconds: 900, isActive: true }
    }
  }
];

const activeTickets = new Map();
const activePlates = new Set();

// Real-Time SSE (Server-Sent Events) Subscribers Pool
let sseClients = [];

function broadcastEvent(eventData) {
  const payload = `data: ${JSON.stringify(eventData)}\n\n`;
  sseClients.forEach(client => {
    try {
      client.res.write(payload);
    } catch (e) {
      // Client disconnected
    }
  });
}

function saveStationsState() {
  try {
    fs.writeFileSync(STATE_FILE, JSON.stringify(stations, null, 2));
  } catch (e) {}
}

function loadStationsState() {
  try {
    if (fs.existsSync(STATE_FILE)) {
      const data = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
      if (Array.isArray(data) && data.length > 0) {
        stations.length = 0;
        stations.push(...data);
      }
    } else {
      saveStationsState();
    }
  } catch (e) {}
}
loadStationsState();

// ==========================================
// REAL-TIME STREAMING ENDPOINT (SSE)
// ==========================================
app.get('/api/events', (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();

  const clientId = Date.now();
  const newClient = { id: clientId, res };
  sseClients.push(newClient);

  // Send initial connection handshake
  res.write(`data: ${JSON.stringify({ type: "CONNECTED", stations })}\n\n`);

  req.on('close', () => {
    sseClients = sseClients.filter(c => c.id !== clientId);
  });
});

// ==========================================
// DRIVER REST APIS & CENTRALIZED STORE
// ==========================================

// 1. Get all stations in Hawassa
app.get('/api/stations', (req, res) => {
  res.json({ success: true, count: stations.length, data: stations });
});

// Universal Centralized Update Endpoint
app.post('/api/stations/update', (req, res) => {
  const { stationId, activePermittedCategory, currentlyServing, category, benzeneStatus, dieselStatus } = req.body;
  const station = stations.find(s => s.id === stationId);
  if (station) {
    if (activePermittedCategory) station.activePermittedCategory = activePermittedCategory;
    if (benzeneStatus) station.benzeneStatus = benzeneStatus;
    if (dieselStatus) station.dieselStatus = dieselStatus;
    if (category && currentlyServing !== undefined && station.lanes[category]) {
      station.lanes[category].currentlyServing = currentlyServing;
    }
    saveStationsState();
    broadcastEvent({
      type: "STATION_FULL_UPDATE",
      stationId,
      station
    });
  }
  res.json({ success: true, count: stations.length, data: stations });
});

// 2. Driver Remote Registration (No vehicle type selection by driver required)
app.post('/api/queue/join', (req, res) => {
  const { stationId, driverPhone, licensePlate, driverName } = req.body;

  if (!stationId || !driverPhone || !licensePlate) {
    return res.status(400).json({ success: false, error: "Missing required fields" });
  }

  const plateClean = licensePlate.trim().toUpperCase();
  if (activePlates.has(plateClean)) {
    return res.status(409).json({ success: false, error: `Vehicle ${plateClean} already has an active queue ticket!` });
  }

  const station = stations.find(s => s.id === stationId);
  if (!station) {
    return res.status(404).json({ success: false, error: "Station not found" });
  }

  // Authoritative vehicle category comes from the Admin-controlled active category
  const targetCategory = station.activePermittedCategory;
  const lane = station.lanes[targetCategory];
  if (!lane || !lane.isActive) {
    return res.status(400).json({ success: false, error: `Lane for ${targetCategory} is currently closed or paused.` });
  }

  lane.lastAssigned += 1;
  const ticketNumber = lane.lastAssigned;
  const ticketId = `TKT-${Date.now()}`;

  const ticket = {
    id: ticketId,
    stationId,
    stationNameEn: station.nameEn,
    stationNameAm: station.nameAm,
    vehicleCategory: targetCategory,
    licensePlate: plateClean,
    driverPhone,
    driverName: driverName || "Driver",
    ticketNumber,
    status: "WAITING",
    registeredAt: new Date().toISOString()
  };

  activeTickets.set(ticketId, ticket);
  activePlates.add(plateClean);

  broadcastEvent({
    type: "DRIVER_JOINED",
    stationId,
    category: targetCategory,
    lastAssigned: lane.lastAssigned,
    waitingCount: Math.max(0, lane.lastAssigned - lane.currentlyServing)
  });

  res.status(201).json({ success: true, data: ticket });
});

// 3. Driver Ticket Status Inquiry
app.get('/api/queue/status/:ticketId', (req, res) => {
  const ticket = activeTickets.get(req.params.ticketId);
  if (!ticket) {
    return res.status(404).json({ success: false, error: "Ticket not found or expired" });
  }

  const station = stations.find(s => s.id === ticket.stationId);
  const lane = station?.lanes[ticket.vehicleCategory];
  const currentlyServing = lane ? lane.currentlyServing : (ticket.ticketNumber - 1);
  const vehiclesAhead = Math.max(0, ticket.ticketNumber - currentlyServing);
  const avgSeconds = lane ? lane.avgDurationSeconds : 120;
  const waitMinutes = Math.floor((vehiclesAhead * avgSeconds) / 60);

  res.json({
    success: true,
    data: {
      ...ticket,
      currentlyServing,
      vehiclesAhead,
      estimatedWaitMinutes: waitMinutes,
      isNearTurn: waitMinutes <= 10 || vehiclesAhead <= 3
    }
  });
});

// ==========================================
// WEB ADMIN COORDINATOR APIS
// ==========================================

// 4. Coordinator Sets & Announces Permitted Vehicle Category for Station
app.post('/api/admin/station/:stationId/permitted-category', (req, res) => {
  const { stationId } = req.params;
  const { category } = req.body;

  const station = stations.find(s => s.id === stationId);
  if (!station) return res.status(404).json({ success: false, error: "Station not found" });

  if (!station.lanes[category]) {
    return res.status(400).json({ success: false, error: "Invalid vehicle category" });
  }

  station.activePermittedCategory = category;
  saveStationsState();

  broadcastEvent({
    type: "PERMITTED_CATEGORY_CHANGED",
    stationId,
    activePermittedCategory: category
  });

  res.json({
    success: true,
    message: `Permitted vehicle category for ${station.nameEn} updated to ${category}`,
    activePermittedCategory: category
  });
});

// 5. Coordinator Calls Next Driver
app.post('/api/admin/station/:stationId/call-next', (req, res) => {
  const { stationId } = req.params;
  const { category } = req.body;

  const station = stations.find(s => s.id === stationId);
  if (!station) return res.status(404).json({ success: false, error: "Station not found" });

  const cat = category || station.activePermittedCategory;
  const lane = station.lanes[cat];
  if (!lane) return res.status(400).json({ success: false, error: "Lane not found" });

  if (lane.currentlyServing < lane.lastAssigned) {
    lane.currentlyServing += 1;
  } else {
    lane.lastAssigned += 1;
    lane.currentlyServing += 1;
  }

  saveStationsState();

  broadcastEvent({
    type: "QUEUE_ADVANCED",
    stationId,
    category: cat,
    currentlyServing: lane.currentlyServing,
    waitingCount: Math.max(0, lane.lastAssigned - lane.currentlyServing)
  });

  res.json({
    success: true,
    message: `Called next driver for ${cat} lane: #${lane.currentlyServing}`,
    currentlyServing: lane.currentlyServing,
    waitingCount: Math.max(0, lane.lastAssigned - lane.currentlyServing)
  });
});

// 6. Coordinator Broadcasts 10-Minute Alert
app.post('/api/admin/station/:stationId/broadcast-alert', (req, res) => {
  const { stationId } = req.params;
  const { category } = req.body;

  broadcastEvent({
    type: "TEN_MINUTE_ALERT_BROADCAST",
    stationId,
    category: category || "ALL",
    timestamp: new Date().toISOString()
  });

  res.json({
    success: true,
    message: `10-Minute warning alert broadcasted to all ${category} drivers waiting at ${stationId}.`,
    alertSentAt: new Date().toISOString()
  });
});

// 7. Coordinator Updates Fuel Stock Status
app.post('/api/admin/station/:stationId/fuel-stock', (req, res) => {
  const { stationId } = req.params;
  const { isBenzene, status } = req.body;

  const station = stations.find(s => s.id === stationId);
  if (!station) return res.status(404).json({ success: false, error: "Station not found" });

  if (isBenzene) {
    station.benzeneStatus = status;
  } else {
    station.dieselStatus = status;
  }

  saveStationsState();

  broadcastEvent({
    type: "FUEL_STATUS_UPDATED",
    stationId,
    isBenzene,
    status
  });

  res.json({ success: true, station });
});

// APK Download endpoint for testing on physical Android devices
app.get(['/download/hawassa-transit-driver.apk', '/download/app-debug.apk'], (req, res) => {
  const candidates = [
    path.join(__dirname, '..', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'),
    path.join(__dirname, '..', 'public', 'hawassa-transit-driver.apk'),
    path.join(__dirname, '..', 'HawassaTransitDriver.apk'),
    path.join(__dirname, '..', 'app-debug.apk')
  ];
  const apkPath = candidates.find((p) => fs.existsSync(p));
  if (apkPath) {
    res.download(apkPath, 'HawassaTransitDriver.apk');
  } else {
    res.status(404).send('APK is currently compiling. Please check back in a few moments.');
  }
});

// Standalone Web Admin route
app.get('/admin', (req, res) => {
  res.sendFile(path.join(__dirname, '..', 'web-admin.html'));
});

// Standalone Driver App route
app.get('/driver', (req, res) => {
  res.sendFile(path.join(__dirname, '..', 'driver-app.html'));
});

// Interactive Unified Preview Environment (Dual Screen PC & Mobile testing)
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, '..', 'index.html'));
});

// Server Initialization - Port 3000 connects directly to AI Studio web preview & Nginx bridge
if (!process.env.VERCEL) {
  const PORT = (process.env.APP_PORT || (process.env.PORT && process.env.PORT !== '8080' ? process.env.PORT : 3000));
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Hawassa Transit Queue REST API & SSE Server running on port ${PORT}`);
  });
}

module.exports = app;
