package com.ticket.shared;

import java.util.UUID;

@FunctionalInterface
public interface UuidSupplier {
    UUID get();
}
