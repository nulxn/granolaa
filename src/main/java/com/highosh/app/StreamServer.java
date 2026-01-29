package com.highosh.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * HTTP server that serves a simple index page and MJPEG streams for screen
 * and webcam. View streams in any browser at /screen and /webcam.
 */
public class StreamServer {

    private static final String BOUNDARY = "frame";
    private static final String MJPEG_CONTENT_TYPE =
            "multipart/x-mixed-replace; boundary=" + BOUNDARY;

    private final HttpServer server;
    private final ScreenCapture screenCapture;
    private final WebcamCapture webcamCapture;

    public StreamServer(int port, ScreenCapture screenCapture, WebcamCapture webcamCapture)
            throws IOException {
        this.screenCapture = screenCapture;
        this.webcamCapture = webcamCapture;
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", this::handleIndex);
        server.createContext("/screen", this::handleScreenStream);
        server.createContext("/webcam", this::handleWebcamStream);
        server.setExecutor(null);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>HighoshWare Streams</title></head>
            <body>
            <h1>HighoshWare – Teacher view</h1>
            <p><a href="/screen">Screen stream</a></p>
            <p><a href="/webcam">Webcam stream</a></p>
            </body>
            </html>
            """;
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void handleScreenStream(HttpExchange exchange) throws IOException {
        if (!screenCapture.isScreenCaptureSupported()) {
            String msg = "Screen capture is not supported on Wayland. Use macOS or Windows for screen streaming.";
            byte[] body = msg.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(503, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            return;
        }
        streamMJPEG(exchange, screenCapture::getLatestFrame);
    }

    private void handleWebcamStream(HttpExchange exchange) throws IOException {
        streamMJPEG(exchange, webcamCapture::getLatestFrame);
    }

    private void streamMJPEG(HttpExchange exchange, java.util.function.Supplier<byte[]> frameSupplier)
            throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", MJPEG_CONTENT_TYPE);
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream out = exchange.getResponseBody()) {
            while (true) {
                byte[] frame = frameSupplier.get();
                if (frame != null && frame.length > 0) {
                    String header = "--" + BOUNDARY + "\r\nContent-Type: image/jpeg\r\nContent-Length: "
                            + frame.length + "\r\n\r\n";
                    out.write(header.getBytes(StandardCharsets.US_ASCII));
                    out.write(frame);
                    out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                }
                Thread.sleep(100);
            }
        } catch (IOException e) {
            // Client disconnected
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
