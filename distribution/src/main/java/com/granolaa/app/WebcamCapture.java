package com.granolaa.app;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameUtils;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captures from the default webcam at a fixed rate and holds the latest JPEG
 * frame for MJPEG streaming. On macOS (x86_64 and aarch64) uses JavaCV
 * (OpenCV) because sarxos webcam-capture often fails there.
 */
public class WebcamCapture implements Runnable {

    private static final int DEFAULT_FPS = 5;
    private static final int CAPTURE_WIDTH = 640;
    private static final int CAPTURE_HEIGHT = 480;

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

    private static boolean isMacOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac");
    }

    @Override
    public void run() {
        if (isMacOs()) {
            runMacOs();
        } else {
            runSarxos();
        }
    }

    /** Webcam capture on macOS using JavaCV (works on x86_64 and aarch64). */
    private void runMacOs() {
        OpenCVFrameGrabber grabber = null;
        try {
            grabber = new OpenCVFrameGrabber(0);
            grabber.setImageWidth(CAPTURE_WIDTH);
            grabber.setImageHeight(CAPTURE_HEIGHT);
            grabber.start();

            long intervalMs = 1000L / Math.max(1, fps);

            while (running) {
                long start = System.currentTimeMillis();
                Frame frame = grabber.grab();
                if (frame != null && frame.image != null) {
                    BufferedImage image = Java2DFrameUtils.toBufferedImage(frame);
                    if (image != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(image, "jpg", baos);
                        latestFrame.set(baos.toByteArray());
                    }
                }

                long elapsed = System.currentTimeMillis() - start;
                long sleep = Math.max(0, intervalMs - elapsed);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
        } catch (Exception e) {
            System.err.println("Webcam capture error (macOS): " + e.getMessage());
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Webcam capture on Windows/Linux using sarxos webcam-capture. */
    private void runSarxos() {
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
