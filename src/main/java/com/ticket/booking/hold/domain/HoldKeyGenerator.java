package com.ticket.booking.hold.domain;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HoldKeyGenerator {

    public String generate() {
        return "HOLD-" + UUID.randomUUID().toString().replace("-", "");
    }
}
