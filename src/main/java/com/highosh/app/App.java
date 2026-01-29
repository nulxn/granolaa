package com.highosh.app;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class App {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = Integer.parseInt(
                System.getProperty("port", System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT))));

        ScreenCapture screenCapture = new ScreenCapture();
        WebcamCapture webcamCapture = new WebcamCapture();

        if (ScreenCapture.isWayland()) {
            System.out.println("Screen capture is not supported on Wayland (supported on macOS and Windows). Only webcam will be available.");
            System.out.println();
        }

        Thread screenThread = new Thread(screenCapture, "screen-capture");
        Thread webcamThread = new Thread(webcamCapture, "webcam-capture");
        screenThread.setDaemon(true);
        webcamThread.setDaemon(true);
        screenThread.start();
        webcamThread.start();

        try {
            StreamServer streamServer = new StreamServer(port, screenCapture, webcamCapture);
            streamServer.start();

            System.out.println("HighoshWare – teacher view streaming");
            System.out.println();
            System.out.println("  Local:   http://localhost:" + port + "/");
            System.out.println("  Screen:  http://localhost:" + port + "/screen");
            System.out.println("  Webcam:  http://localhost:" + port + "/webcam");
            System.out.println();
            printNetworkUrls(port);
            System.out.println();
            System.out.println("Press Ctrl+C to stop.");
        } catch (IOException e) {
            System.err.println("Failed to start stream server: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printNetworkUrls(int port) {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (nic.isLoopback() || !nic.isUp()) continue;
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress() || addr.getHostAddress().contains(":")) continue;
                    System.out.println("  Network: http://" + addr.getHostAddress() + ":" + port + "/");
                }
            }
        } catch (Exception ignored) {
        }
    }
}
