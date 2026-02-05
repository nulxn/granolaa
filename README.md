# Granolaa

A distributed streaming system for screen and webcam capture. The system consists of:
- **Distribution Client**: Java application that captures screen/webcam and sends streams to a server
- **Server**: Node.js server that receives multiple streams and serves a web interface for viewing

## Architecture

- **Person sending (distribution client)**: The Java app sends screen and webcam frames to the server using **HTTP POST** (chunked) to `/stream/screen` and `/stream/webcam`. It does **not** use WebSocket.
- **Person watching**: A viewer opens the server URL in a **browser**. The browser connects to the server using **WebSocket** (`/view`) to receive the list of streams and live frames.

```
┌─────────────────┐      HTTP POST (chunked)    ┌──────────────┐
│  Client (Java)  │  /stream/screen, /stream/   │   Server     │
│  - Screen       │  webcam                     │  (Node.js)   │
│  - Webcam       │ ─────────────────────────> │              │
└─────────────────┘                             │  ┌────────┐  │
                                               │  │ Viewer│  │
                                               │  │ (WS)  │  │
                                               │  └───┬───┘  │
                                               └──────│──────┘
                                                      │ WebSocket /view
                                                      ▼
                                               ┌──────────────┐
                                               │   Browser    │
                                               │   (viewer)   │
                                               └──────────────┘
```

## Quick Start

### 1. Start the Server

```bash
cd server
npm install
npm start
```

The server will run on `http://localhost:3000` by default.

### 2. Run Client(s)

In a separate terminal, run one or more clients:

```bash
cd distribution
mvn clean compile exec:java
```

Or with a custom server URL:

```bash
mvn exec:java -Dserver=http://your-server.com:3000
```

### 3. View Streams

Open your browser and navigate to `http://localhost:3000` (or your server URL) to view all active streams.

## Configuration

### Client Configuration

The distribution client uses **HTTP POST** to send streams (not WebSocket). Set the server URL via:
- System property: `-Dserver=http://server-url:port`
- Environment variable: `SERVER_URL=http://server-url:port`
- Default: `http://granolaa.opencodingsociety.com` (server URL is http, not https)


### Server Configuration

Set the port via:
- Environment variable: `PORT=3000`
- Default: `3000`

## Building

### Client (Distribution)

```bash
cd distribution
mvn clean package
```

This creates a JAR file in `target/granolaa-1.0-SNAPSHOT.jar`.

Run the JAR:
```bash
java -jar target/granolaa-1.0-SNAPSHOT.jar
# or with custom server:
java -Dserver=http://your-server.com:3000 -jar target/granolaa-1.0-SNAPSHOT.jar
```

### Server

The server uses Node.js and doesn't require building. Just install dependencies:

```bash
cd server
npm install
```

## Features

- **Multiple Streams**: Support for multiple clients streaming simultaneously
- **Screen & Webcam**: Each client can stream both screen and webcam
- **Real-time**: Low-latency streaming via HTTP POST (client → server) and WebSocket (server → viewer)
- **Web Interface**: Modern, responsive web interface for viewing streams
- **Auto-reconnect**: Client and viewer automatically reconnect on connection loss

## Platform Support

- **Screen Capture**: macOS and Windows (not supported on Linux Wayland)
- **Webcam**: macOS (using JavaCV), Windows, and Linux (using sarxos)

## URLs

- **Server Web Interface**: `http://localhost:3000/`
- **Stream source (client → server)**: HTTP POST `http://localhost:3000/stream/screen` and `/stream/webcam` (chunked, length-prefixed frames)
- **WebSocket View Endpoint**: `ws://localhost:3000/view` (browsers receive stream updates)

## Development

### Client Development

```bash
cd distribution
mvn clean compile exec:java
```

### Server Development

```bash
cd server
npm install
npm start
```

## Notes

**Webcam on macOS:** Webcam capture uses JavaCV (OpenCV) on macOS (Intel and Apple Silicon) because the default sarxos driver often fails there.

**Screen Capture on Linux:** Screen capture is not supported on Linux Wayland. Use macOS or Windows for screen streaming.
