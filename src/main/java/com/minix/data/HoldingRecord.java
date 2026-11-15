package com.minix.data;

/** One row of the "holdings" table: how many shares of a stock a user owns. (Java record) */
public record HoldingRecord(String symbol, int quantity, double avgPrice) { }
