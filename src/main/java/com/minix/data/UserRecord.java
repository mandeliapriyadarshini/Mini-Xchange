package com.minix.data;

import java.util.List;

/**
 * A user as stored in the database. A Java RECORD is a short way to write an immutable
 * class: the constructor, getters (id(), username()...), equals and toString are generated.
 */
public record UserRecord(long id, String username, String passwordHash,
                         double cash, List<HoldingRecord> holdings) { }
