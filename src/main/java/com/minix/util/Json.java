package com.minix.util;

import java.util.Map;

/**
 * A tiny JSON writer, so the project needs no external JSON library.
 *
 * It turns Java objects into JSON text using RECURSION:
 *   Map -> {...},  List/Collection -> [...],  String -> "...",  numbers/booleans as-is,
 *   any JsonSerializable -> its toJson() map.
 */
public final class Json {

    private Json() { }   // utility class: no objects needed

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof JsonSerializable js) {
            write(sb, js.toJson());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else if (value instanceof Double d) {
            sb.append(d.isNaN() || d.isInfinite() ? "null" : formatNumber(d));
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            // Strings, enums and anything else are written as text
            writeString(sb, value.toString());
        }
    }

    private static String formatNumber(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
