package com.minix.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Serves the website files (HTML, CSS, JS) that are packed inside the jar under /public.
 * Visiting "/" returns index.html (the login page).
 */
public class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        // refuse paths like "/../secret" so nobody can read files outside /public
        if (path.contains("..")) {
            HttpUtil.send(ex, 400, "text/plain", "Bad path".getBytes(StandardCharsets.UTF_8));
            return;
        }

        try (InputStream in = getClass().getResourceAsStream("/public" + path)) {
            if (in == null) {
                HttpUtil.send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            HttpUtil.send(ex, 200, TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }
}
