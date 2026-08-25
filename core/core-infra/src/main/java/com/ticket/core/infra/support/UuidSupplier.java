package com.ticket.core.infra.support;

import java.util.UUID;

@FunctionalInterface
public interface UuidSupplier {
    UUID get();
}
