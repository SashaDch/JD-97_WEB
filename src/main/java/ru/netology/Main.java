package ru.netology;

import ru.netology.server.Server;
import ru.netology.server.SimpleHttpServer;

import java.io.*;

public class Main {
  public static void main(String[] args) throws Exception {
      Server server = new SimpleHttpServer(9999);
      server.start();
      System.in.read();
      server.stop();
  }
}


