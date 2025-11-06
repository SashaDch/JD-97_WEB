package ru.netology.server;

import java.util.Map;

public interface Request {
    String getMethod();
    String getPath();
    Map<String, String> getParams();
    String getParam(String name);
    Map<String, String> getHeaders();
    String getHeader(String name);
    String getBody();
}
