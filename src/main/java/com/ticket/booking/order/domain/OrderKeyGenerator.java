package com.ticket.booking.order.domain;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class OrderKeyGenerator {
    public String generate() {
        return "ORDER-" + UUID.randomUUID().toString().replace("-", "");
    }
}
