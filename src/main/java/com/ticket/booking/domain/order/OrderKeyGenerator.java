package com.ticket.booking.domain.order;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class OrderKeyGenerator {
    public String generate() {
        return "ORDER-" + UUID.randomUUID().toString().replace("-", "");
    }
}
