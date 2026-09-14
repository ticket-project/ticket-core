package com.ticket.payment.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.payment.domain.Payment;

interface SpringDataPaymentJpaRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentKey(String paymentKey);

    List<Payment> findAllByOrderIdOrderByAttemptNoAsc(Long orderId);
}
