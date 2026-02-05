package com.granolaa.app;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captures the primary screen at a fixed rate and holds the latest JPEG frame
 * for MJPEG streaming. Not supported on Linux Wayland (macOS and Windows only).
 */
public class ScreenCapture implements Runnable {

    private static final int DEFAULT_FPS = 5;
    private static final String WAYLAND_DISPLAY = "WAYLAND_DISPLAY";
    private static final String XDG_SESSION_TYPE = "XDG_SESSION_TYPE";

    /** Returns true if running on Linux under Wayland (screen capture unsupported). */
    public static boolean isWayland() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) return false;
        String waylandDisplay = System.getenv(WAYLAND_DISPLAY);
        String xdgSession = System.getenv(XDG_SESSION_TYPE);
        return (waylandDisplay != null && !waylandDisplay.isEmpty())
                || "wayland".equalsIgnoreCase(xdgSession != null ? xdgSession : "");
    }

    /** Returns false on Wayland (screen capture not supported there). */
    public boolean isScreenCaptureSupported() {
        return !isWayland();
    }
    private static final double DEFAULT_SCALE = 0.5; // half size to reduce bandwidth

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>(new byte[0]);
    private volatile boolean running = true;
    private final int fps;
    private final double scale;

    public ScreenCapture() {
        this(DEFAULT_FPS, DEFAULT_SCALE);
    }

    public ScreenCapture(int fps, double scale) {
        this.fps = fps;
        this.scale = scale;
    }

    public byte[] getLatestFrame() {
        return latestFrame.get();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        if (isWayland()) {
            return; // Screen capture not supported on Wayland; only webcam will stream
        }
        try {
            Robot robot = new Robot();
            Rectangle captureArea = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            long intervalMs = 1000L / Math.max(1, fps);

            while (running) {
                long start = System.currentTimeMillis();
                BufferedImage raw = robot.createScreenCapture(captureArea);

                int w = (int) (raw.getWidth() * scale);
                int h = (int) (raw.getHeight() * scale);
                if (w < 1) w = 1;
                if (h < 1) h = 1;

                BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(raw, 0, 0, w, h, null);
                g.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(scaled, "jpg", baos);
                latestFrame.set(baos.toByteArray());

                long elapsed = System.currentTimeMillis() - start;
                long sleep = Math.max(0, intervalMs - elapsed);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
        } catch (AWTException e) {
            System.err.println("Screen capture failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Screen capture error: " + e.getMessage());
        }
    }
}
