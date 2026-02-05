package com.granolaa.app;

import java.util.Scanner;

public class App {

    private static final String DEFAULT_SERVER_URL = "http://granolaa.opencodingsociety.com";

    public static void main(String[] args) {
        String serverUrl = System.getProperty("server.url",
                System.getProperty("server",
                        System.getenv().getOrDefault("SERVER_URL", DEFAULT_SERVER_URL)));

        ScreenCapture screenCapture = new ScreenCapture();
        WebcamCapture webcamCapture = new WebcamCapture();

        Thread screenThread = new Thread(screenCapture, "screen-capture");
        Thread webcamThread = new Thread(webcamCapture, "webcam-capture");
        screenThread.setDaemon(true);
        webcamThread.setDaemon(true);
        screenThread.start();
        webcamThread.start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        StreamClient streamClient = new StreamClient(serverUrl, screenCapture, webcamCapture);
        streamClient.start();

        System.out.println("Server: " + serverUrl + "  Client ID: " + streamClient.getClientId());
        System.out.println("Sending once per second. Press Enter to stop.");

        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        }

        streamClient.stop();
        screenCapture.stop();
        webcamCapture.stop();
        System.out.println("Stopped.");
    }
}
