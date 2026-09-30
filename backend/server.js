require('dotenv').config();
const express = require('express');
const admin = require('firebase-admin');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.urlencoded({ extended: true }));
app.use(express.json());

// Target FCM Registration Token state (reads from .env or runtime config)
let targetDeviceToken = process.env.TARGET_DEVICE_TOKEN || 'PASTE_YOUR_FCM_REGISTRATION_TOKEN_HERE';
let isFirebaseInitialized = false;
let firebaseInitError = null;

// Initialize Firebase Admin SDK
function initFirebase() {
  try {
    // 1. Check for raw JSON string in environment variable (Render / Cloud best practice)
    if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
      const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      isFirebaseInitialized = true;
      console.log('✅ Firebase Admin SDK initialized from FIREBASE_SERVICE_ACCOUNT_JSON environment variable.');
      return;
    }

    // 2. Check for Base64 encoded JSON in environment variable
    if (process.env.FIREBASE_SERVICE_ACCOUNT_BASE64) {
      const decoded = Buffer.from(process.env.FIREBASE_SERVICE_ACCOUNT_BASE64, 'base64').toString('utf8');
      const serviceAccount = JSON.parse(decoded);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      isFirebaseInitialized = true;
      console.log('✅ Firebase Admin SDK initialized from FIREBASE_SERVICE_ACCOUNT_BASE64 environment variable.');
      return;
    }

    // 3. Fallback to local JSON file
    const customPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './firebase-service-account.json';
    const resolvedPath = path.resolve(__dirname, customPath);

    if (fs.existsSync(resolvedPath)) {
      const serviceAccount = require(resolvedPath);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      isFirebaseInitialized = true;
      console.log('✅ Firebase Admin SDK initialized from file:', resolvedPath);
    } else {
      firebaseInitError = `Firebase Service Account key missing. Set FIREBASE_SERVICE_ACCOUNT_JSON env var on Render or place firebase-service-account.json in the backend directory.`;
      console.warn(`⚠️ Warning: ${firebaseInitError}`);
    }
  } catch (err) {
    firebaseInitError = err.message;
    console.error('❌ Failed to initialize Firebase Admin SDK:', err.message);
  }
}

initFirebase();

// ============================================================================
// 1. Unblocked Public Health & Keepalive Endpoints (Bypasses User-Agent block)
// ============================================================================
app.get('/healthz', (req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'Wakey Backend',
    uptime: Math.floor(process.uptime()),
    timestamp: new Date().toISOString()
  });
});

app.get('/api/ping', (req, res) => {
  res.status(200).send('PONG');
});

// ============================================================================
// 2. Security Middleware to block Mobile Browsers from Disarming Alarms
// ============================================================================
function blockMobileBrowsers(req, res, next) {
  const userAgent = req.headers['user-agent'] || '';
  const isMobile = /Mobi|Android|iPhone|iPad|iPod|Windows Phone|IEMobile|BlackBerry|webOS|Opera Mini/i.test(userAgent);

  if (isMobile) {
    return res.status(403).send(`
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Access Denied - Desktop Only</title>
        <style>
          body {
            margin: 0;
            padding: 2rem;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            background: #0f172a;
            color: #f87171;
            font-family: system-ui, -apple-system, sans-serif;
            text-align: center;
          }
          .card {
            background: #1e293b;
            border: 2px solid #ef4444;
            border-radius: 16px;
            padding: 2.5rem;
            max-width: 500px;
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
          }
          h1 {
            font-size: 1.8rem;
            margin-bottom: 1rem;
            color: #ef4444;
          }
          p {
            font-size: 1.1rem;
            line-height: 1.6;
            color: #cbd5e1;
          }
          .badge {
            display: inline-block;
            background: #450a0a;
            color: #fca5a5;
            padding: 0.5rem 1rem;
            border-radius: 9999px;
            font-weight: bold;
            margin-bottom: 1rem;
          }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="badge">🛑 MOBILE ACCESS DENIED</div>
          <h1>Get Out of Bed!</h1>
          <p>Nice try! Mobile browsers cannot dismiss this alarm. You must walk to your desktop computer or laptop browser to disarm the alarm.</p>
        </div>
      </body>
      </html>
    `);
  }
  next();
}

// System Status API
app.get('/api/status', blockMobileBrowsers, (req, res) => {
  res.json({
    firebaseInitialized: isFirebaseInitialized,
    firebaseError: firebaseInitError,
    hasTargetToken: targetDeviceToken !== 'PASTE_YOUR_FCM_REGISTRATION_TOKEN_HERE' && targetDeviceToken.trim().length > 0,
    maskedToken: targetDeviceToken && targetDeviceToken.length > 10 
      ? `${targetDeviceToken.substring(0, 6)}...${targetDeviceToken.substring(targetDeviceToken.length - 6)}`
      : 'Not configured',
    serverTime: new Date().toISOString()
  });
});

// Update Target FCM Token API
app.post('/api/token', blockMobileBrowsers, (req, res) => {
  const { token } = req.body;
  if (!token || typeof token !== 'string' || token.trim().length < 10) {
    return res.status(400).json({ error: 'Valid FCM token string is required.' });
  }
  targetDeviceToken = token.trim();
  console.log('🔄 Target FCM device token updated:', `${targetDeviceToken.substring(0, 8)}...`);
  res.json({ success: true, message: 'Device token updated successfully.' });
});

// Serve UI Interface
app.get('/', blockMobileBrowsers, (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

// Handle Verification & Disarm Action
app.post('/dismiss-alarm', blockMobileBrowsers, async (req, res) => {
  const isJson = req.headers['content-type']?.includes('application/json') || req.xhr;

  if (!isFirebaseInitialized) {
    const errorMsg = 'Firebase Admin is not configured. Please configure FIREBASE_SERVICE_ACCOUNT_JSON in environment variables or upload the JSON key.';
    if (isJson) return res.status(500).json({ success: false, error: errorMsg });
    return res.status(500).send(`<h1 style="color:red; font-family:sans-serif; text-align:center; margin-top:20%;">${errorMsg}</h1>`);
  }

  const tokenToUse = (req.body && req.body.token && req.body.token.trim()) || targetDeviceToken;

  if (!tokenToUse || tokenToUse === 'PASTE_YOUR_FCM_REGISTRATION_TOKEN_HERE' || tokenToUse.trim().length < 10) {
    const errorMsg = 'Target FCM Device Token is missing. Update it in the dashboard or in environment variables.';
    if (isJson) return res.status(400).json({ success: false, error: errorMsg });
    return res.status(400).send(`<h1 style="color:orange; font-family:sans-serif; text-align:center; margin-top:20%;">${errorMsg}</h1>`);
  }

  const message = {
    data: {
      action: 'STOP_ALARM',
      timestamp: Date.now().toString()
    },
    token: tokenToUse,
    android: {
      priority: 'high' // Ensures instant wake-up on deep-sleeping devices
    }
  };

  try {
    const response = await admin.messaging().send(message);
    console.log('🚀 FCM Disarm signal dispatched successfully:', response);

    if (isJson) {
      return res.json({ success: true, messageId: response, message: 'Alarm disarmed successfully! Good morning!' });
    }

    res.send(`
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Wakey // Alarm Disarmed</title>
        <style>
          body {
            margin: 0;
            padding: 2rem;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            background: #0f172a;
            color: #4ade80;
            font-family: system-ui, -apple-system, sans-serif;
            text-align: center;
          }
          .card {
            background: #1e293b;
            border: 2px solid #22c55e;
            border-radius: 16px;
            padding: 2.5rem;
            max-width: 500px;
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
          }
          h1 { color: #22c55e; margin-bottom: 0.5rem; }
          p { color: #cbd5e1; margin-bottom: 1.5rem; }
          a {
            display: inline-block;
            background: #FF6600;
            color: white;
            text-decoration: none;
            padding: 0.75rem 1.5rem;
            border-radius: 8px;
            font-weight: bold;
          }
        </style>
      </head>
      <body>
        <div class="card">
          <h1>✅ ALARM DISARMED</h1>
          <p>The high-priority kill command was successfully sent to your Android device. Good morning!</p>
          <a href="/">Return to Dashboard</a>
        </div>
      </body>
      </html>
    `);
  } catch (error) {
    console.error('❌ FCM Delivery Failure:', error);
    if (isJson) {
      return res.status(500).json({ success: false, error: 'FCM Delivery failure: ' + error.message });
    }
    res.status(500).send(`
      <div style="color:red; font-family:sans-serif; text-align:center; margin-top:15%;">
        <h1>FCM Delivery Failure</h1>
        <p>${error.message}</p>
        <p><a href="/" style="color:#FF6600;">Back to Dashboard</a></p>
      </div>
    `);
  }
});

// ============================================================================
// 3. Built-in Auto-Keepalive Cron Engine (Pings server every 10 minutes)
// ============================================================================
function setupKeepAlive() {
  const externalUrl = process.env.RENDER_EXTERNAL_URL || process.env.SELF_PING_URL || process.env.SERVER_URL;
  
  if (!externalUrl) {
    console.log('ℹ️ Auto-keepalive idle (No RENDER_EXTERNAL_URL or SELF_PING_URL set).');
    return;
  }

  const pingEndpoint = externalUrl.endsWith('/') ? `${externalUrl}api/ping` : `${externalUrl}/api/ping`;
  const PING_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes (Render sleeps after 15 mins)

  console.log(`⏰ Initializing built-in keep-alive cron job for: ${pingEndpoint}`);

  setInterval(() => {
    try {
      const client = pingEndpoint.startsWith('https') ? https : http;
      client.get(pingEndpoint, (res) => {
        console.log(`📡 [KeepAlive Ping] ${new Date().toISOString()} -> Status: ${res.statusCode}`);
      }).on('error', (err) => {
        console.warn(`⚠️ [KeepAlive Ping Warning] ${err.message}`);
      });
    } catch (e) {
      console.warn(`⚠️ [KeepAlive Exception] ${e.message}`);
    }
  }, PING_INTERVAL_MS);
}

app.listen(PORT, '0.0.0.0', () => {
  console.log(`====================================================`);
  console.log(`🚀 Wakey Desktop Verification Server running on Port ${PORT}`);
  console.log(`👉 Access on this PC: http://localhost:${PORT}`);
  console.log(`====================================================`);
  setupKeepAlive();
});
