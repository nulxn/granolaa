const express = require('express');
const WebSocket = require('ws');
const http = require('http');
const path = require('path');

const app = express();
const server = http.createServer(app);

// Request logging middleware
app.use((req, res, next) => {
    const start = Date.now();
    const timestamp = new Date().toISOString();
    const ip = req.ip || req.connection.remoteAddress || req.headers['x-forwarded-for'] || 'unknown';
    
    console.log(`[${timestamp}] ${req.method} ${req.url} from ${ip}`);
    if (Object.keys(req.query).length > 0) {
        console.log(`  Query params:`, req.query);
    }
    if (req.headers['host']) {
        console.log(`  Host: ${req.headers['host']}`);
    }
    
    // Log response when it finishes
    res.on('finish', () => {
        const duration = Date.now() - start;
        console.log(`[${timestamp}] ${req.method} ${req.url} -> ${res.statusCode} (${duration}ms)`);
    });
    
    res.on('close', () => {
        if (!res.finished) {
            console.log(`[${timestamp}] ${req.method} ${req.url} -> connection closed (client disconnected)`);
        }
    });
    
    next();
});

// Let's Encrypt / Certbot ACME challenge (must be before other routes)
const acmeDir = process.env.ACME_CHALLENGE_DIR || __dirname;
app.use('/.well-known/acme-challenge', express.static(path.join(acmeDir, '.well-known', 'acme-challenge'), { index: false }));

// Serve static files from public directory
app.use(express.static(path.join(__dirname, 'public')));

// Store active streams: clientId -> { screen: true|false, webcam: true|false } (presence only; frames come via HTTP)
const activeStreams = new Map();

// HTTP POST endpoints for stream sources (avoids WebSocket control-frame issues with Java/OkHttp)
// Body: repeated [4-byte big-endian length][length bytes JPEG]. No body parser — read raw stream.
app.post('/stream/screen', (req, res) => {
    const clientId = req.query.clientId;
    console.log(`[STREAM] POST /stream/screen - clientId: ${clientId || 'MISSING'}`);
    if (!clientId) {
        console.error(`[STREAM] Missing clientId for /stream/screen`);
        res.status(400).send('Missing clientId');
        return;
    }
    registerStream(clientId, 'screen');
    req.on('data', (chunk) => { feedChunk(clientId, 'screen', chunk); });
    req.on('end', () => { unregisterStream(clientId, 'screen'); });
    req.on('error', (err) => { 
        console.error(`[STREAM] Error in /stream/screen for clientId ${clientId}:`, err);
        unregisterStream(clientId, 'screen'); 
    });
    res.status(200).end();
});

app.post('/stream/webcam', (req, res) => {
    const clientId = req.query.clientId;
    console.log(`[STREAM] POST /stream/webcam - clientId: ${clientId || 'MISSING'}`);
    if (!clientId) {
        console.error(`[STREAM] Missing clientId for /stream/webcam`);
        res.status(400).send('Missing clientId');
        return;
    }
    registerStream(clientId, 'webcam');
    req.on('data', (chunk) => { feedChunk(clientId, 'webcam', chunk); });
    req.on('end', () => { unregisterStream(clientId, 'webcam'); });
    req.on('error', (err) => { 
        console.error(`[STREAM] Error in /stream/webcam for clientId ${clientId}:`, err);
        unregisterStream(clientId, 'webcam'); 
    });
    res.status(200).end();
});

// Per-connection buffers for length-prefixed frame parsing
const streamBuffers = new Map();

function bufferKey(clientId, streamType) {
    return `${clientId}:${streamType}`;
}

function registerStream(clientId, streamType) {
    if (!activeStreams.has(clientId)) {
        activeStreams.set(clientId, { screen: false, webcam: false });
    }
    activeStreams.get(clientId)[streamType] = true;
    streamBuffers.set(bufferKey(clientId, streamType), { buf: Buffer.alloc(0), need: 4, payloadLen: 0 });
    broadcastStreamUpdate();
    console.log(`Client ${clientId} connected with ${streamType} stream (HTTP)`);
}

function feedChunk(clientId, streamType, chunk) {
    const key = bufferKey(clientId, streamType);
    let state = streamBuffers.get(key);
    if (!state) return;
    state.buf = Buffer.concat([state.buf, chunk]);
    while (true) {
        if (state.need === 4 && state.buf.length >= 4) {
            const len = state.buf.readUInt32BE(0);
            state.buf = state.buf.subarray(4);
            if (len > 10 * 1024 * 1024) { state.need = 4; state.payloadLen = 0; break; }
            state.payloadLen = len;
            state.need = len;
        }
        if (state.need > 0 && state.buf.length >= state.need) {
            const frame = state.buf.subarray(0, state.need);
            state.buf = state.buf.subarray(state.need);
            broadcastFrame(clientId, streamType, frame);
            state.need = 4;
            state.payloadLen = 0;
            continue;
        }
        break;
    }
}

function unregisterStream(clientId, streamType) {
    streamBuffers.delete(bufferKey(clientId, streamType));
    const clientStreams = activeStreams.get(clientId);
    if (clientStreams) {
        clientStreams[streamType] = false;
        if (!clientStreams.screen && !clientStreams.webcam) {
            activeStreams.delete(clientId);
        }
    }
    broadcastStreamUpdate();
    console.log(`Client ${clientId} disconnected ${streamType} stream`);
}

// WebSocket server for viewing clients (browsers)
const viewWss = new WebSocket.Server({ 
    server,
    path: '/view',
    perMessageDeflate: false
});

const viewingClients = new Set();

viewWss.on('connection', (ws) => {
    viewingClients.add(ws);
    console.log('Viewing client connected. Total viewers:', viewingClients.size);
    
    // Send current stream list
    sendStreamList(ws);

    ws.on('close', () => {
        viewingClients.delete(ws);
        console.log('Viewing client disconnected. Total viewers:', viewingClients.size);
    });

    ws.on('error', (error) => {
        console.error('Viewing client error:', error);
    });
});

function broadcastFrame(clientId, streamType, frameData) {
    const data = Buffer.isBuffer(frameData) ? frameData.toString('base64') : frameData.toString('base64');
    const message = JSON.stringify({
        type: 'frame',
        clientId,
        streamType,
        data
    });

    viewingClients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            try {
                client.send(message);
            } catch (error) {
                console.error('Error sending frame to viewing client:', error);
            }
        }
    });
}

function broadcastStreamUpdate() {
    const streamList = Array.from(activeStreams.entries()).map(([clientId, streams]) => ({
        clientId,
        hasScreen: streams.screen === true,
        hasWebcam: streams.webcam === true
    }));

    const message = JSON.stringify({
        type: 'streams',
        streams: streamList
    });

    viewingClients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            try {
                client.send(message);
            } catch (error) {
                console.error('Error sending stream update:', error);
            }
        }
    });
}

function sendStreamList(ws) {
    const streamList = Array.from(activeStreams.entries()).map(([clientId, streams]) => ({
        clientId,
        hasScreen: streams.screen === true,
        hasWebcam: streams.webcam === true
    }));

    const message = JSON.stringify({
        type: 'streams',
        streams: streamList
    });

    if (ws.readyState === WebSocket.OPEN) {
        ws.send(message);
    }
}

// 404 handler - log all unmatched requests
app.use((req, res) => {
    console.error(`[404] ${req.method} ${req.url} - No route matched`);
    console.error(`  Host: ${req.headers['host'] || 'unknown'}`);
    console.error(`  User-Agent: ${req.headers['user-agent'] || 'unknown'}`);
    console.error(`  Referer: ${req.headers['referer'] || 'none'}`);
    console.error(`  Query:`, req.query);
    console.error(`  Available routes: GET /, POST /stream/screen, POST /stream/webcam, WS /view`);
    res.status(404).send('Not Found');
});

// Error handler
app.use((err, req, res, next) => {
    console.error(`[ERROR] ${req.method} ${req.url}:`, err);
    console.error(`  Stack:`, err.stack);
    res.status(err.status || 500).send(err.message || 'Internal Server Error');
});

// Handle uncaught errors
process.on('uncaughtException', (err) => {
    console.error('Uncaught Exception:', err);
    console.error('Stack:', err.stack);
});

process.on('unhandledRejection', (reason, promise) => {
    console.error('Unhandled Rejection at:', promise, 'reason:', reason);
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Granolaa server running on http://localhost:${PORT}`);
    console.log(`WebSocket stream endpoint: ws://localhost:${PORT}/stream`);
    console.log(`WebSocket view endpoint: ws://localhost:${PORT}/view`);
    console.log(`ACME challenge dir: ${acmeDir}`);
    console.log(`Public dir: ${path.join(__dirname, 'public')}`);
});
