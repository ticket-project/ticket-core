package com.ticket.member.infrastructure;

import java.util.UUID;

@FunctionalInterface
public interface UuidSupplier {
    UUID get();
}
