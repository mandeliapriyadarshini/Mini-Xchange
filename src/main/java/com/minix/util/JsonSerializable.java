package com.minix.util;

import java.util.Map;

/**
 * INTERFACE: an object that knows how to describe itself as JSON for the website.
 * Each class decides which of its fields are safe to show (for example, Trade shows the
 * buyer's username but never the buyer's cash).
 */
public interface JsonSerializable {
    Map<String, Object> toJson();
}
