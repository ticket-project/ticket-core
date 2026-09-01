package com.ticket.core.domain.order.command.create;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderKeyGenerator {

    public String generate() {
        return "ORDER-" + UUID.randomUUID().toString().replace("-", "");
    }
}
