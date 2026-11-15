package com.minix.model;

import com.minix.exceptions.InvalidOrderException;

/**
 * INTERFACE: anything that can check its own data before the exchange accepts it.
 * Order implements it, and each subclass adds its own rules by overriding validate().
 */
public interface Validatable {
    void validate() throws InvalidOrderException;
}
