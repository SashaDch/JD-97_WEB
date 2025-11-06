package ru.netology.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class SimpleHttpServer implements CustomizableServer, AutoCloseable {
    private static final int DEFAULT_THREAD_POOL_SIZE = 64;
    private static final int DEFAULT_SERVER_PORT = 9999;
    private static final long OVERLOAD_ACCEPT_TIMEOUT = 5000;
    private static final int SOCKET_TIMEOUT = 5000;
    private static final int ACCEPT_TIMEOUT = 1000;
    private static final List<String> VALID_PATHS = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private static final Handler DEFAULT_HANDLER = (request, responseStream) -> {
        try {
            if (request == null) {
                badRequest(responseStream);
                return;
            }
            String path = request.getPath();
            if (request.getMethod() == null || !request.getMethod().equals("GET") || !VALID_PATHS.contains(path)) {
                notFound(responseStream);
                return;
            }
            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);
            final var length = Files.size(filePath);
            responseStream.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, responseStream);
            responseStream.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    };

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Thread acceptLoopThread;
    private final int threadPoolSize;
    private final int serverPort;
    private final Map<String, Map<String, Handler>> handlers = new HashMap<>();

    private volatile boolean running = false;

    @Override
    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }
        try {
            threadPool = Executors.newFixedThreadPool(threadPoolSize);
            serverSocket = new ServerSocket(serverPort);
            serverSocket.setSoTimeout(ACCEPT_TIMEOUT);
        } catch (Throwable t) {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
            }
            if (threadPool != null) {
                threadPool.shutdown();
            }
            throw new RuntimeException(t);
        }
        acceptLoopThread = new Thread(this::acceptLoop);
        running = true;
        acceptLoopThread.start();
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(OVERLOAD_ACCEPT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                threadPool.shutdownNow();
                if (!threadPool.awaitTermination(OVERLOAD_ACCEPT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Server thread pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            threadPool.shutdownNow();
        }
        try {
            acceptLoopThread.join(OVERLOAD_ACCEPT_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        stop();
    }

    @Override
    public synchronized void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new HashMap<>());
        }
        handlers.get(method).put(path, handler);
    }


    private void acceptLoop() {
        while (running) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                final Socket finalSocket = socket;
                threadPool.execute(() -> {
                    try (finalSocket) {
                        finalSocket.setSoTimeout(SOCKET_TIMEOUT);
                        process(finalSocket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                socket = null;
            } catch (SocketTimeoutException ignored) {
            } catch (SocketException se) {
                if (!running) {
                    break;
                }
                se.printStackTrace();
            } catch (RejectedExecutionException re) {
                if (!running) {
                    break;
                }
                try {
                    Thread.sleep(OVERLOAD_ACCEPT_TIMEOUT);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    public void process(Socket socket) {
        try {
            final var in = new BufferedInputStream(socket.getInputStream());
            final var out = new BufferedOutputStream(socket.getOutputStream());
            Request request = MyRequest.parse(in);
            Handler handler = DEFAULT_HANDLER;
            if (request != null && handlers.containsKey(request.getMethod())) {
                handler = handlers.get(request.getMethod()).getOrDefault(request.getPath(), DEFAULT_HANDLER);
            }
            handler.handle(request, out);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static void badRequest(BufferedOutputStream responseStream) throws IOException {
        responseStream.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        responseStream.flush();
    }

    private static void notFound(BufferedOutputStream responseStream) throws IOException {
        responseStream.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        responseStream.flush();
    }


    public SimpleHttpServer() {
        this(DEFAULT_SERVER_PORT);
    }

    public SimpleHttpServer(int serverPort) {
        this(serverPort, DEFAULT_THREAD_POOL_SIZE);
    }

    public SimpleHttpServer(int serverPort, int threadPoolSize) {
        this.serverPort = serverPort;
        this.threadPoolSize = threadPoolSize;
    }

}
