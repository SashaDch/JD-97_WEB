package ru.netology.server;

import java.io.BufferedOutputStream;
public interface Handler {
    void handle(Request request, BufferedOutputStream responseStream);
}
