const WS_URL = `ws://${window.location.host}/view`;
let ws = null;
let streams = new Map(); // clientId -> { screen: ImageData, webcam: ImageData }
let reconnectTimeout = null;
let hasReceivedFrame = false; // Track if we've ever received a frame

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
    
    streamCountEl.textContent = streamList.length;

    // Remove streams that are no longer active
    const activeClientIds = new Set(streamList.map(s => s.clientId));
    streams.forEach((data, clientId) => {
        if (!activeClientIds.has(clientId)) {
            streams.delete(clientId);
            const card = document.getElementById(`stream-${clientId}`);
            if (card) card.remove();
        }
    });

    // Add or update stream cards
    streamList.forEach(stream => {
        if (!streams.has(stream.clientId)) {
            streams.set(stream.clientId, { screen: null, webcam: null });
        }

        if (stream.hasScreen) {
            ensureStreamCard(stream.clientId, 'screen');
        }
        if (stream.hasWebcam) {
            ensureStreamCard(stream.clientId, 'webcam');
        }
    });

    // Show/hide no streams message - hide permanently once we've received any frame
    if (hasReceivedFrame) {
        noStreamsEl.style.display = 'none';
    } else if (streamList.length === 0) {
        noStreamsEl.style.display = 'block';
    } else {
        noStreamsEl.style.display = 'none';
    }
}

function ensureStreamCard(clientId, streamType) {
    const cardId = `stream-${clientId}-${streamType}`;
    let card = document.getElementById(cardId);
    
    if (!card) {
        card = createStreamCard(clientId, streamType);
        const container = document.getElementById('streamsContainer');
        container.appendChild(card);
    }
}

function createStreamCard(clientId, streamType) {
    const card = document.createElement('div');
    card.id = `stream-${clientId}-${streamType}`;
    card.className = 'stream-card';
    
    card.innerHTML = `
        <div class="stream-header">
            <div>
                <span class="stream-title">${streamType === 'screen' ? 'Screen' : 'Webcam'}</span>
                <span class="stream-type ${streamType}">${streamType}</span>
            </div>
            <div class="stream-client-id">${clientId.substring(0, 8)}...</div>
        </div>
        <div class="stream-content">
            <img id="img-${clientId}-${streamType}" class="stream-image" alt="${streamType} stream">
            <div id="placeholder-${clientId}-${streamType}" class="stream-placeholder">Waiting for frames...</div>
        </div>
    `;
    
    return card;
}

function handleFrame(message) {
    const { clientId, streamType, data } = message;
    const imgId = `img-${clientId}-${streamType}`;
    const placeholderId = `placeholder-${clientId}-${streamType}`;
    
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
