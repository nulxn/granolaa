package com.granolaa.app;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sends frames to the server at 10 FPS. Same request format as SendOneFrameTest.
 */
public class StreamClient {

    private static final long SEND_INTERVAL_MS = 100; // 10 FPS
    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");

    private final String baseUrl;
    private final String clientId;
    private final ScreenCapture screenCapture;
    private final WebcamCapture webcamCapture;
    private final OkHttpClient client;
    private volatile boolean running = true;
    private Thread screenThread;
    private Thread webcamThread;

    public StreamClient(String serverUrl, ScreenCapture screenCapture, WebcamCapture webcamCapture) {
        this.baseUrl = serverUrl;
        this.clientId = UUID.randomUUID().toString();
        this.screenCapture = screenCapture;
        this.webcamCapture = webcamCapture;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    public void start() {
        if (screenCapture.isScreenCaptureSupported()) {
            screenThread = new Thread(this::runScreen, "screen-sender");
            screenThread.setDaemon(true);
            screenThread.start();
        }
        webcamThread = new Thread(this::runWebcam, "webcam-sender");
        webcamThread.setDaemon(true);
        webcamThread.start();
    }

    public void stop() {
        running = false;
        if (screenThread != null) screenThread.interrupt();
        if (webcamThread != null) webcamThread.interrupt();
    }

    public String getClientId() {
        return clientId;
    }

    private void runScreen() {
        String url = baseUrl + "/stream/screen?clientId=" + clientId;
        int n = 0;
        while (running) {
            byte[] frame = screenCapture.getLatestFrame();
            if (frame == null || frame.length == 0) frame = minimalJpeg();
            sendOne(url, frame, "screen", ++n);
            sleep();
        }
    }

    private void runWebcam() {
        String url = baseUrl + "/stream/webcam?clientId=" + clientId;
        int n = 0;
        while (running) {
            byte[] frame = webcamCapture.getLatestFrame();
            if (frame == null || frame.length == 0) frame = minimalJpeg();
            sendOne(url, frame, "webcam", ++n);
            sleep();
        }
    }

    private void sendOne(String url, byte[] frame, String name, int count) {
        ByteBuffer buf = ByteBuffer.allocate(4 + frame.length);
        buf.putInt(frame.length);
        buf.put(frame);
        byte[] body = buf.array();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, OCTET_STREAM))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (count == 1) System.out.println("[" + name + "] POST " + url + " -> " + response.code());
            if (!response.isSuccessful()) System.err.println("[" + name + "] " + response.code());
            // Consume response body to avoid connection issues
            if (response.body() != null) {
                response.body().bytes(); // Read and discard
            }
        } catch (IOException e) {
            System.err.println("[" + name + "] " + e.getMessage());
        }
    }

    private void sleep() {
        try {
            Thread.sleep(SEND_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] minimalJpeg() {
        return new byte[] {
                (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, (byte)0x00, (byte)0x10, (byte)0x4A, (byte)0x46,
                (byte)0x49, (byte)0x46, (byte)0x00, (byte)0x01, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x01,
                (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0xFF, (byte)0xDB, (byte)0x00, (byte)0x43,
                (byte)0x00, (byte)0x08, (byte)0x06, (byte)0x06, (byte)0x07, (byte)0x06, (byte)0x05, (byte)0x08,
                (byte)0x07, (byte)0x07, (byte)0x07, (byte)0x09, (byte)0x09, (byte)0x08, (byte)0x0A, (byte)0x0C,
                (byte)0x14, (byte)0x0D, (byte)0x0C, (byte)0x0B, (byte)0x0B, (byte)0x0C, (byte)0x19, (byte)0x12,
                (byte)0x13, (byte)0x0F, (byte)0x14, (byte)0x1D, (byte)0x1A, (byte)0x1F, (byte)0x1E, (byte)0x1D,
                (byte)0x1A, (byte)0x1C, (byte)0x1C, (byte)0x20, (byte)0x24, (byte)0x2E, (byte)0x27, (byte)0x20,
                (byte)0x22, (byte)0x2C, (byte)0x23, (byte)0x1C, (byte)0x1C, (byte)0x28, (byte)0x37, (byte)0x29,
                (byte)0x2C, (byte)0x30, (byte)0x31, (byte)0x34, (byte)0x34, (byte)0x34, (byte)0x1F, (byte)0x27,
                (byte)0x39, (byte)0x3D, (byte)0x38, (byte)0x32, (byte)0x3C, (byte)0x2E, (byte)0x33, (byte)0x34,
                (byte)0x32, (byte)0xFF, (byte)0xC0, (byte)0x00, (byte)0x0B, (byte)0x08, (byte)0x00, (byte)0x01,
                (byte)0x00, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x11, (byte)0x00, (byte)0xFF, (byte)0xC4,
                (byte)0x00, (byte)0x1F, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x05, (byte)0x01, (byte)0x01,
                (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03,
                (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08, (byte)0x09, (byte)0x0A, (byte)0x0B,
                (byte)0xFF, (byte)0xDA, (byte)0x00, (byte)0x08, (byte)0x01, (byte)0x01, (byte)0x00, (byte)0x00,
                (byte)0x3F, (byte)0x00, (byte)0x7B, (byte)0xDF, (byte)0xFF, (byte)0xD9
        };
    }
}
