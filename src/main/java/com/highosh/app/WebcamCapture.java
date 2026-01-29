package com.highosh.app;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captures from the default webcam at a fixed rate and holds the latest JPEG
 * frame for MJPEG streaming.
 */
public class WebcamCapture implements Runnable {

    private static final int DEFAULT_FPS = 10;

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>(new byte[0]);
    private volatile boolean running = true;
    private final int fps;

    public WebcamCapture() {
        this(DEFAULT_FPS);
    }

    public WebcamCapture(int fps) {
        this.fps = fps;
    }

    public byte[] getLatestFrame() {
        return latestFrame.get();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        Webcam webcam = null;
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                System.err.println("No webcam found. Webcam stream will be unavailable.");
                return;
            }
            Dimension size = WebcamResolution.VGA.getSize();
            webcam.setViewSize(size);
            webcam.open();

            long intervalMs = 1000L / Math.max(1, fps);

            while (running && webcam.isOpen()) {
                long start = System.currentTimeMillis();
                BufferedImage image = webcam.getImage();
                if (image != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "jpg", baos);
                    latestFrame.set(baos.toByteArray());
                }

                long elapsed = System.currentTimeMillis() - start;
                long sleep = Math.max(0, intervalMs - elapsed);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
        } catch (Exception e) {
            System.err.println("Webcam capture error: " + e.getMessage());
        } finally {
            if (webcam != null && webcam.isOpen()) {
                webcam.close();
            }
        }
    }
}
