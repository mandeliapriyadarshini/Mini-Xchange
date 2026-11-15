package com.minix.exceptions;

/**
 * Wraps a database error (SQLException) so the rest of the app does not depend on JDBC.
 * It is UNCHECKED (extends RuntimeException) because a broken database is not something
 * the user can fix by changing their input.
 */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
