package com.ticket.booking.domain.hold;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class HoldKeyGenerator {
    public String generate() {
        return "HOLD-" + UUID.randomUUID().toString().replace("-", "");
    }
}
