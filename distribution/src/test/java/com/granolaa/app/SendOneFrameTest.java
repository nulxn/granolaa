package com.granolaa.app;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Sends exactly one webcam frame to the server and logs the full response.
 * Run with: mvn test -Dtest=SendOneFrameTest
 * Server URL: -Dserver.url=http://localhost:3000 or env SERVER_URL
 */
public class SendOneFrameTest {

    private static final String DEFAULT_SERVER_URL = "http://localhost:3000";
    private static final long WEBCAM_WAIT_MS = 15_000;

    public static void main(String[] args) throws IOException, InterruptedException {
        run();
    }

    /** Run from JUnit or main: captures one webcam frame, sends it, logs response. */
    public static void run() throws IOException, InterruptedException {
        String baseUrl = System.getProperty("server.url",
                System.getenv().getOrDefault("SERVER_URL", DEFAULT_SERVER_URL));

        String clientId = UUID.randomUUID().toString();
        String url = baseUrl + "/stream/webcam?clientId=" + clientId;

        // Capture one frame from webcam
        System.out.println("--- Capturing one webcam frame ---");
        byte[] frame = captureOneWebcamFrame();
        System.out.println("Captured frame: " + frame.length + " bytes");
        System.out.println();

        ByteBuffer buf = ByteBuffer.allocate(4 + frame.length);
        buf.putInt(frame.length);
        buf.put(frame);
        byte[] body = buf.array();

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, MediaType.get("application/octet-stream")))
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        System.out.println("--- Sending one frame ---");
        System.out.println("POST " + url);
        System.out.println("Body size: " + body.length + " bytes (4-byte length + " + frame.length + " byte frame)");
        System.out.println();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("--- Server response ---");
            System.out.println("Code: " + response.code());
            System.out.println("Message: " + response.message());
            System.out.println("Headers:");
            response.headers().forEach(h -> System.out.println("  " + h.getFirst() + ": " + h.getSecond()));
            ResponseBody respBody = response.body();
            if (respBody != null) {
                String bodyStr = respBody.string();
                System.out.println("Body: " + (bodyStr.isEmpty() ? "(empty)" : bodyStr));
            } else {
                System.out.println("Body: (null)");
            }
            System.out.println("---");
            System.out.println(response.isSuccessful() ? "SUCCESS" : "FAILED");
        }
    }

    @Test
    public void sendOneFrameAndLogResponse() throws IOException, InterruptedException {
        run();
    }

    /** Starts webcam capture, waits for one non-empty frame, stops capture, returns the frame. */
    private static byte[] captureOneWebcamFrame() throws InterruptedException {
        WebcamCapture webcam = new WebcamCapture();
        Thread t = new Thread(webcam, "webcam-capture");
        t.setDaemon(true);
        t.start();

        long deadline = System.currentTimeMillis() + WEBCAM_WAIT_MS;
        byte[] frame;
        while (System.currentTimeMillis() < deadline) {
            frame = webcam.getLatestFrame();
            if (frame != null && frame.length > 0) {
                webcam.stop();
                return frame;
            }
            Thread.sleep(100);
        }
        webcam.stop();
        throw new IllegalStateException("No webcam frame received within " + (WEBCAM_WAIT_MS / 1000) + "s. Is a webcam connected and not in use by another app?");
    }
}
