package com.minix.web;

import com.minix.util.Json;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/** Small helpers for reading requests and writing responses with Java's built-in HTTP server. */
public final class HttpUtil {

    private HttpUtil() { }

    /**
     * Parses "symbol=DSRT&side=BUY&quantity=10" into a Map.
     * The website sends form data in this format (and so do URL query strings).
     */
    public static Map<String, String> parseParams(String text) {
        Map<String, String> params = new HashMap<>();
        if (text == null || text.isEmpty()) return params;
        StringTokenizer pairs = new StringTokenizer(text, "&");
        while (pairs.hasMoreTokens()) {
            String pair = pairs.nextToken();
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            params.put(decode(key), decode(value));
        }
        return params;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    /** Reads the request body (form data) plus the URL query string into one map. */
    public static Map<String, String> readParams(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(ex.getRequestURI().getRawQuery());
        try (InputStream in = ex.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            params.putAll(parseParams(body));
        }
        return params;
    }

    public static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.stringify(body).getBytes(StandardCharsets.UTF_8));
    }

    public static void send(HttpExchange ex, int status, String contentType, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Reads a required whole number, with a friendly error if it is missing or not a number. */
    public static int requireInt(Map<String, String> p, String name) throws BadRequestException {
        try {
            return Integer.parseInt(require(p, name).trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("'" + name + "' must be a whole number.");
        }
    }

    public static double requireDouble(Map<String, String> p, String name) throws BadRequestException {
        try {
            return Double.parseDouble(require(p, name).trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("'" + name + "' must be a number.");
        }
    }

    public static String require(Map<String, String> p, String name) throws BadRequestException {
        String v = p.get(name);
        if (v == null || v.isBlank()) throw new BadRequestException("Missing '" + name + "'.");
        return v;
    }

    /** Thrown when the request itself is malformed (missing or non-numeric fields). */
    public static class BadRequestException extends Exception {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
