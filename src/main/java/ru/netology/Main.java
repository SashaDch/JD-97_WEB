package ru.netology;

import ru.netology.server.CustomizableServer;
import ru.netology.server.SimpleHttpServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) throws Exception {
        CustomizableServer server = new SimpleHttpServer(9999);

        server.addHandler("GET", "/classic.html", (request, out) -> {
            try {
                String path = request.getPath();
                final var filePath = Path.of(".", "public", path);
                final var mimeType = Files.probeContentType(filePath);
                // special case for classic
                if (path.equals("/classic.html")) {
                    final var template = Files.readString(filePath);
                    final var content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()).getBytes();
                    out.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + content.length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.write(content);
                    out.flush();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        server.start();
        System.in.read();
        server.stop();
    }
}