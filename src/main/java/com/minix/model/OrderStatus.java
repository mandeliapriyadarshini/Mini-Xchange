package com.minix.model;

/** Life cycle of an order inside the exchange. */
public enum OrderStatus {
    OPEN,              // waiting in the order book, nothing filled yet
    PARTIALLY_FILLED,  // some shares traded, the rest is still waiting
    FILLED,            // every share traded
    CANCELLED          // removed by the user, or a market order that ran out of sellers/buyers
}
