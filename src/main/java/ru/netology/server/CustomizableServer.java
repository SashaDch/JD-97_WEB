package ru.netology.server;

public interface CustomizableServer extends Server {
    void addHandler(String method, String path, Handler handler);
}
