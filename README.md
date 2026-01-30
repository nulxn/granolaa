# Granolaa

While the app is running, the student's screen and webcam are streamed to local HTTP URLs that can be opened in any browser.

## Commands

Run the application:

```bash
mvn clean compile exec:java
```

Optional: set port (default 8080):

```bash
mvn exec:java -Dport=9090
```

Package (JAR in `target`):

```bash
mvn clean package
```

Run the JAR:

```bash
java -jar target/highoshware-1.0-SNAPSHOT.jar
# or with port:
java -Dport=9090 -jar target/highoshware-1.0-SNAPSHOT.jar
```

## URLs

After starting, open in a browser:

- **Index:** `http://localhost:8080/` – links to both streams
- **Screen:** `http://localhost:8080/screen` – live screen capture (MJPEG)
- **Webcam:** `http://localhost:8080/webcam` – live webcam (MJPEG)

On the same network, use the printed "Network" URL (e.g. `http://192.168.x.x:8080/`) so other devices can view the streams.

**Webcam on macOS:** Webcam capture uses JavaCV (OpenCV) on macOS (Intel and Apple Silicon) because the default sarxos driver often fails there. “Network” URL (e.g. `http://192.168.x.x:8080/`) so other devices can view the streams.
