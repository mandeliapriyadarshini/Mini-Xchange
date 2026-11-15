package com.minix.exceptions;

/**
 * Parent of every "expected" problem in the exchange (not enough money, bad input...).
 *
 * It is a CHECKED exception (extends Exception), so Java forces any code that places an
 * order to either catch it or declare it. The website catches it and shows the message.
 * Child classes below form an EXCEPTION HIERARCHY, so code can catch one specific
 * problem, or all of them at once by catching ExchangeException.
 */
public class ExchangeException extends Exception {
    public ExchangeException(String message) {
        super(message);
    }
}
