package com.minix.web;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Starts Java's built-in web server (com.sun.net.httpserver, part of the JDK) and
 * connects each URL prefix to the class that handles it.
 */
public class WebServer {

    private final HttpServer server;

    public WebServer(int port, ApiHandler api, LiveFeed liveFeed, StaticFileHandler files) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/stream", liveFeed);   // live prices (most specific path first)
        server.createContext("/api/", api);              // JSON API
        server.createContext("/", files);                // HTML / CSS / JS
        // a pool of threads, so many browsers (and their long-lived live-feed connections)
        // can be served at the same time
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }
}
