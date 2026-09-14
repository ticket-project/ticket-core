package com.ticket.security.token;

import java.util.UUID;

@FunctionalInterface
public interface UuidSupplier {
    UUID get();
}
