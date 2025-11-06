package ru.netology.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.*;

public class MyRequest implements Request {
    private static final int REQUEST_LINE_LIMIT = 4096;

    private String method;
    private String path;
    private final Map<String, String> params = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    private String body = null;


    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST");

    private MyRequest() {
    }

    public static Request parse(BufferedInputStream in) {
        MyRequest request = new MyRequest();

        try {
            in.mark(REQUEST_LINE_LIMIT);
            final var buffer = new byte[REQUEST_LINE_LIMIT];
            final var read = in.read(buffer);

            // ищем request line
            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                return null;
            }

            // читаем request line
            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split("\\s+");
            if (requestLine.length != 3) {
                return null;
            }

            request.method = requestLine[0];
            if (!ALLOWED_METHODS.contains(request.method)) {
                return null;
            }
            System.out.println(request.method);

            final var pathAndParams = requestLine[1].split("\\?", 2);

            request.path = pathAndParams[0];
            if (!request.path.startsWith("/")) {
                return null;
            }
            System.out.println(request.path);

            if (pathAndParams.length > 1) {
                if (!pathAndParams[1].matches("[^&?=]+=[^&?=]+(&[^&?=]+=[^&?=]+)*")) {
                    return null;
                }
                for (String param : pathAndParams[1].split("&")) {
                    request.params.put(
                            param.split("=", 2)[0],
                            param.split("=", 2)[1]);
                }
                System.out.println(request.params);
            }

            // ищем заголовки
            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                return null;
            }

            // отматываем на начало буфера
            in.reset();
            // пропускаем requestLine
            in.skip(headersStart);

            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            for (String header : headers) {
                request.headers.put(header.split(": ")[0], header.split(": ")[1]);
            }
            System.out.println(headers);
            System.out.println(request.headers);

            // для GET тела нет
            if (!request.method.equals("GET")) {
                in.skip(headersDelimiter.length);
                // вычитываем Content-Length, чтобы прочитать body
                final var contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()) {
                    final var length = Integer.parseInt(contentLength.get());
                    final var bodyBytes = in.readNBytes(length);

                    request.body = new String(bodyBytes);
                    System.out.println(request.body);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return request;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Map<String, String> getParams() {
        return params;
    }

    @Override
    public String getParam(String name) {
        return params.get(name);
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public String getBody() {
        return body;
    }


    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}

