const WS_URL = `wss://${window.location.host}/view`;
let ws = null;
// clientId -> { hasScreen, hasWebcam, lastSeen }
let streams = new Map();
let reconnectTimeout = null;
let hasReceivedFrame = false; // Track if we've ever received a frame
let pinnedClients = new Set(); // clientIds pinned by the viewer
let lastStreamList = [];
const STREAM_TTL_MS = 5000; // keep clients \"active\" for a few seconds to avoid flicker

// Load pinned clients from localStorage
try {
    const stored = JSON.parse(localStorage.getItem('pinnedClients') || '[]');
    if (Array.isArray(stored)) {
        pinnedClients = new Set(stored);
    }
} catch (e) {
    pinnedClients = new Set();
}

function connect() {
    const statusEl = document.getElementById('status');
    statusEl.textContent = 'Connecting...';
    statusEl.className = 'status connecting';

    ws = new WebSocket(WS_URL);

    ws.onopen = () => {
        console.log('Connected to server');
        statusEl.textContent = 'Connected';
        statusEl.className = 'status connected';
        if (reconnectTimeout) {
            clearTimeout(reconnectTimeout);
            reconnectTimeout = null;
        }
    };

    ws.onmessage = (event) => {
        try {
            const message = JSON.parse(event.data);
            
            if (message.type === 'streams') {
                handleStreamsUpdate(message.streams);
            } else if (message.type === 'frame') {
                handleFrame(message);
            }
        } catch (error) {
            console.error('Error parsing message:', error);
        }
    };

    ws.onclose = () => {
        console.log('Disconnected from server');
        statusEl.textContent = 'Disconnected';
        statusEl.className = 'status disconnected';
        
        // Attempt to reconnect after 3 seconds
        if (!reconnectTimeout) {
            reconnectTimeout = setTimeout(() => {
                reconnectTimeout = null;
                connect();
            }, 3000);
        }
    };

    ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        statusEl.textContent = 'Connection Error';
        statusEl.className = 'status disconnected';
    };
}

function handleStreamsUpdate(streamList) {
    const container = document.getElementById('streamsContainer');
    const noStreamsEl = document.getElementById('noStreams');
    const streamCountEl = document.getElementById('streamCount');

    const now = Date.now();

    // Update or create entries for streams we just heard about
    streamList.forEach(s => {
        const existing = streams.get(s.clientId) || { hasScreen: false, hasWebcam: false, lastSeen: now };
        streams.set(s.clientId, {
            hasScreen: s.hasScreen,
            hasWebcam: s.hasWebcam,
            lastSeen: now
        });
    });

    // Build a stable list of \"active\" clients, pruning ones that have been gone for a while
    const active = [];
    streams.forEach((info, clientId) => {
        if (now - info.lastSeen <= STREAM_TTL_MS && (info.hasScreen || info.hasWebcam)) {
            active.push({ clientId, hasScreen: info.hasScreen, hasWebcam: info.hasWebcam });
        } else if (now - info.lastSeen > STREAM_TTL_MS) {
            streams.delete(clientId);
        }
    });

    lastStreamList = active.slice();
    streamCountEl.textContent = active.length;

    // Sort: pinned clients first, then others
    const sorted = active.slice().sort((a, b) => {
        const aPinned = pinnedClients.has(a.clientId);
        const bPinned = pinnedClients.has(b.clientId);
        if (aPinned && !bPinned) return -1;
        if (!aPinned && bPinned) return 1;
        return a.clientId.localeCompare(b.clientId);
    });
    // Ensure cards exist and are in the right order, but do NOT recreate them each update
    sorted.forEach(stream => {
        ensureClientCard(stream, container);
    });

    // Show/hide no streams message - hide permanently once we've received any frame
    if (hasReceivedFrame) {
        noStreamsEl.style.display = 'none';
    } else if (active.length === 0) {
        noStreamsEl.style.display = 'block';
    } else {
        noStreamsEl.style.display = 'none';
    }
}

function ensureClientCard(stream, container) {
    const { clientId, hasScreen, hasWebcam } = stream;
    let card = document.getElementById(`stream-${clientId}`);
    const isPinned = pinnedClients.has(clientId);

    if (!card) {
        card = document.createElement('div');
        card.id = `stream-${clientId}`;
        card.className = 'stream-card';
        card.dataset.clientId = clientId;

        card.innerHTML = `
            <div class="stream-header">
                <div class="stream-header-left">
                    <span class="stream-title">Client</span>
                    <span class="stream-type ${hasScreen ? 'screen' : 'webcam'}">
                        ${hasScreen && hasWebcam ? 'Screen + Webcam' : hasScreen ? 'Screen' : 'Webcam'}
                    </span>
                </div>
                <div class="stream-header-right">
                    <button class="expand-btn" data-client-id="${clientId}">Expand</button>
                    <button class="pin-btn ${isPinned ? 'pinned' : ''}" data-client-id="${clientId}">
                        ${isPinned ? 'Unpin' : 'Pin'}
                    </button>
                    <div class="stream-client-id">${clientId.substring(0, 8)}...</div>
                </div>
            </div>
            <div class="stream-content">
                <img id="img-${clientId}-main" class="stream-image stream-image-main" alt="main stream">
                <div id="placeholder-${clientId}-main" class="stream-placeholder">Waiting for frames...</div>
                <div id="pip-${clientId}" class="pip-container" style="display: ${hasScreen && hasWebcam ? 'block' : 'none'};">
                    <img id="img-${clientId}-pip" class="stream-image stream-image-pip" alt="webcam stream">
                    <div id="placeholder-${clientId}-pip" class="stream-placeholder pip-placeholder">Waiting for webcam...</div>
                </div>
            </div>
        `;
    } else {
        // Update existing card header + pip visibility without recreating DOM
        const typeBadge = card.querySelector('.stream-type');
        if (typeBadge) {
            typeBadge.classList.toggle('screen', !!hasScreen);
            typeBadge.classList.toggle('webcam', !hasScreen);
            typeBadge.textContent = hasScreen && hasWebcam ? 'Screen + Webcam' : hasScreen ? 'Screen' : 'Webcam';
        }

        const pinBtn = card.querySelector('.pin-btn');
        if (pinBtn) {
            pinBtn.classList.toggle('pinned', isPinned);
            pinBtn.textContent = isPinned ? 'Unpin' : 'Pin';
        }

        const pip = card.querySelector(`#pip-${clientId}`);
        if (pip) {
            pip.style.display = hasScreen && hasWebcam ? 'block' : 'none';
        }
    }

    // Attach card-level expand toggle once (click anywhere except buttons)
    if (!card.dataset.expandHandlerAttached) {
        card.addEventListener('click', (e) => {
            if (e.target.closest('button')) return;
            card.classList.toggle('expanded');
            const expandBtn = card.querySelector('.expand-btn');
            if (expandBtn) {
                expandBtn.textContent = card.classList.contains('expanded') ? 'Shrink' : 'Expand';
            }
        });
        card.dataset.expandHandlerAttached = '1';
    }

    // Re-append to enforce order without destroying the element
    container.appendChild(card);
}

function handleFrame(message) {
    const { clientId, streamType, data } = message;
    const streamInfo = streams.get(clientId) || { hasScreen: false, hasWebcam: false };

    // Determine whether this frame should go to main or PiP
    let target = 'main';
    if (streamInfo.hasScreen && streamInfo.hasWebcam) {
        if (streamType === 'webcam') target = 'pip';
        else target = 'main';
    } else {
        target = 'main';
    }

    const imgId = `img-${clientId}-${target}`;
    const placeholderId = `placeholder-${clientId}-${target}`;

    const img = document.getElementById(imgId);
    const placeholder = document.getElementById(placeholderId);

    if (img && placeholder) {
        // Mark that we've received at least one frame, hide "no streams" permanently
        if (!hasReceivedFrame) {
            hasReceivedFrame = true;
            const noStreamsEl = document.getElementById('noStreams');
            if (noStreamsEl) noStreamsEl.style.display = 'none';
        }

        // Convert base64 to blob URL for better performance
        const base64Data = `data:image/jpeg;base64,${data}`;
        img.src = base64Data;
        img.style.display = 'block';
        placeholder.style.display = 'none';
    }
}

function refreshStreams() {
    if (ws && ws.readyState === WebSocket.OPEN) {
        // Request stream list update
        ws.send(JSON.stringify({ type: 'refresh' }));
    } else {
        connect();
    }
}

// Initialize connection on page load
connect();

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    if (ws) {
        ws.close();
    }
    if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
    }
});

// Handle pin button clicks (event delegation)
document.addEventListener('click', (event) => {
    const pinBtn = event.target.closest('.pin-btn');
    const expandBtn = event.target.closest('.expand-btn');

    // Pin/unpin
    if (pinBtn) {
        const clientId = pinBtn.dataset.clientId;
        if (!clientId) return;

        if (pinnedClients.has(clientId)) {
            pinnedClients.delete(clientId);
        } else {
            pinnedClients.add(clientId);
        }

        // Persist to localStorage
        try {
            localStorage.setItem('pinnedClients', JSON.stringify(Array.from(pinnedClients)));
        } catch (e) {
            console.warn('Failed to save pinned clients to localStorage', e);
        }

        // Re-render with updated pin order
        if (lastStreamList.length > 0) {
            handleStreamsUpdate(lastStreamList);
        } else {
            refreshStreams();
        }
        return;
    }

    // Expand/shrink
    if (expandBtn) {
        const clientId = expandBtn.dataset.clientId;
        const card = document.getElementById(`stream-${clientId}`);
        if (!card) return;
        card.classList.toggle('expanded');
        expandBtn.textContent = card.classList.contains('expanded') ? 'Shrink' : 'Expand';
    }
});
