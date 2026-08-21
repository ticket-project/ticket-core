package com.ticket.core.domain.payment.support;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentKeyGenerator {

    public String generate() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "");
    }
}
